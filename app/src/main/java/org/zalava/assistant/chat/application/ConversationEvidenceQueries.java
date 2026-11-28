package org.zalava.assistant.chat.application;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.port.in.AgentRunQueries;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.chat.application.port.out.ConversationEvidenceSupplement;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.out.ToolArgumentDecoder;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.web.control.application.port.in.InvocationLogQueries;

/** Composes bounded, owner-scoped conversation evidence and current approval outcomes. */
public final class ConversationEvidenceQueries {
  private static final Logger LOG = LoggerFactory.getLogger(ConversationEvidenceQueries.class);
  private final ActorChatQueries conversations;
  private final AgentRunQueries runs;
  private final InvocationLogQueries tools;
  private final ModelBoundary boundary;
  private final ZalavaToolApprovalRequests approvals;
  private final ToolArgumentDecoder decoder;
  private final List<ConversationEvidenceSupplement> supplements;

  public ConversationEvidenceQueries(
      ActorChatQueries conversations,
      AgentRunQueries runs,
      InvocationLogQueries tools,
      ModelBoundary boundary,
      ZalavaToolApprovalRequests approvals,
      ToolArgumentDecoder decoder,
      List<ConversationEvidenceSupplement> supplements) {
    this.conversations = conversations;
    this.runs = runs;
    this.tools = tools;
    this.boundary = boundary;
    this.approvals = approvals;
    this.decoder = decoder;
    this.supplements = List.copyOf(supplements);
  }

  public Evidence evidence(Actor actor, String conversationId) {
    var reference = new ConversationReference(conversationId);
    if (!conversations.conversations(actor).contains(reference)) throw new EvidenceNotFound();
    String internalId = new ActorConversationId(actor, reference).value();
    return new Evidence(
        runs.recent().stream()
            .filter(run -> run.conversationId().equals(internalId))
            .sorted(Comparator.comparing(AgentRun::startedAt).reversed())
            .limit(50)
            .map(
                run ->
                    new Run(
                        run.id(),
                        run.startedAt().toString(),
                        run.completedAt().toString(),
                        run.status().name(),
                        run.durationMillis(),
                        run.selectedToolCount(),
                        run.contextCharactersUsed(),
                        run.contextCharacterBudget(),
                        boundary.redact(run.promptPreview()),
                        boundary.redact(run.resultPreview()),
                        boundary.redact(run.errorPreview()),
                        run.contextSourceMetrics()))
            .toList(),
        toolEvidence(actor, internalId));
  }

  private List<Tool> toolEvidence(Actor actor, String internalId) {
    Set<String> observedApprovals = new HashSet<>();
    Map<ExecutionKey, Deque<Integer>> observedExecutions = new HashMap<>();
    List<Tool> evidence = new ArrayList<>();
    for (var tool :
        tools.recentEntries().stream()
            .filter(
                entry ->
                    internalId.equals(entry.conversationId())
                        && actor.accountId().toString().equals(entry.actorId()))
            .limit(50)
            .toList()) {
      String approvalId = approvalId(tool);
      if (approvalId != null) observedApprovals.add(approvalId);
      ExecutionKey executionKey = standingKey(tool);
      if (executionKey != null)
        observedExecutions
            .computeIfAbsent(executionKey, ignored -> new ArrayDeque<>())
            .add(evidence.size());
      String approvalStatus = approvalState(tool, actor, internalId);
      evidence.add(
          new Tool(
              tool.timestamp(),
              tool.providerId(),
              tool.toolName(),
              tool.success(),
              tool.durationMillis(),
              boundary.redact(tool.errorMessage()),
              boundary.redact(tool.resultPreview()),
              boundary.redact(tool.inputPreview()),
              tool.runId(),
              "pending_approval".equals(tool.errorType()) && approvalStatus.equals("pending"),
              approvalStatus,
              null));
    }
    for (var supplement : supplements) {
      try {
        evidence.addAll(supplement.retainedApprovals(actor, internalId, observedApprovals));
        for (var execution : supplement.executions(actor, internalId)) {
          Deque<Integer> observed =
              observedExecutions.get(
                  new ExecutionKey(
                      execution.tool().provider(),
                      execution.tool().name(),
                      execution.executionId()));
          if (observed != null && !observed.isEmpty()) {
            int index = observed.removeFirst();
            evidence.set(index, evidence.get(index).withActionOutcome(execution.outcome()));
          } else {
            evidence.add(execution.tool());
          }
        }
      } catch (RuntimeException unavailable) {
        LOG.warn("Could not read supplemental conversation evidence");
      }
    }
    return evidence.stream()
        .sorted(Comparator.comparing((Tool tool) -> evidenceTimestamp(tool.at())).reversed())
        .limit(50)
        .toList();
  }

  private static Instant evidenceTimestamp(String timestamp) {
    try {
      return Instant.parse(timestamp);
    } catch (RuntimeException invalid) {
      return Instant.EPOCH;
    }
  }

  private ExecutionKey standingKey(InvocationLogQueries.Entry tool) {
    if (!tool.confirmed()
        || tool.runId() == null
        || (tool.errorType() != null
            && !Set.of("provider_failure", "execution", "validation", "unsupported")
                .contains(tool.errorType()))) return null;
    return tool.auditExecutionId() == null
        ? null
        : new ExecutionKey(tool.providerId(), tool.toolName(), tool.auditExecutionId());
  }

  private record ExecutionKey(String provider, String tool, String executionId) {}

  private String approvalId(InvocationLogQueries.Entry tool) {
    if (!"pending_approval".equals(tool.errorType())) return null;
    try {
      var result = decoder.decode(tool.resultPreview());
      if (result.get("content") instanceof Map<?, ?> content
          && content.get("approvalRequestId") instanceof String requestId) return requestId;
    } catch (RuntimeException unavailable) {
      // A malformed or expired preview is not an approval reference.
    }
    return null;
  }

  private String approvalState(
      InvocationLogQueries.Entry tool, Actor actor, String conversationId) {
    if (!"pending_approval".equals(tool.errorType())) return "";
    try {
      var result = decoder.decode(tool.resultPreview());
      if (!(result.get("content") instanceof Map<?, ?> content)) return "unknown";
      if (!(content.get("approvalRequestId") instanceof String requestId)) return "unknown";
      var entry = approvals.get(requestId);
      if (!actor.accountId().toString().equals(entry.actorId())
          || !conversationId.equals(entry.attributes().get("conversationId"))) return "unknown";
      return approvalStatus(entry);
    } catch (RuntimeException unavailable) {
      return "unknown";
    }
  }

  private static String approvalStatus(ZalavaToolApprovalRequests.Entry entry) {
    return switch (entry.decision()) {
      case PENDING -> "pending";
      case DENIED, REVOKED -> "denied";
      case ALLOWED ->
          entry.attributes().getOrDefault(ZalavaToolApprovalRequests.EXECUTION_OUTCOME, "unknown");
    };
  }

  public static final class EvidenceNotFound extends RuntimeException {}

  public record Evidence(List<Run> runs, List<Tool> tools) {}

  public record Run(
      String id,
      String at,
      String completedAt,
      String status,
      long durationMillis,
      int selectedToolCount,
      int contextCharactersUsed,
      int contextCharacterBudget,
      String prompt,
      String result,
      String error,
      List<AgentRun.ContextSourceMetric> contextSources) {}

  public record Tool(
      String at,
      String provider,
      String name,
      boolean success,
      Long durationMillis,
      String error,
      String result,
      String input,
      String runId,
      boolean pendingApproval,
      String approvalStatus,
      String actionOutcome) {
    private Tool withActionOutcome(String outcome) {
      return new Tool(
          at,
          provider,
          name,
          success,
          durationMillis,
          error,
          result,
          input,
          runId,
          pendingApproval,
          approvalStatus,
          outcome);
    }
  }
}
