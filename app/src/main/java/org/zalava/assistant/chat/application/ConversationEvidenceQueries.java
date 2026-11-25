package org.zalava.assistant.chat.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.port.in.AgentRunQueries;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.out.ToolArgumentDecoder;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.InvocationChannelContext;
import org.zalava.web.control.application.port.in.InvocationLogQueries;

/** Composes bounded, owner-scoped conversation evidence and current approval outcomes. */
public final class ConversationEvidenceQueries {
  private final ActorChatQueries conversations;
  private final AgentRunQueries runs;
  private final InvocationLogQueries tools;
  private final ModelBoundary boundary;
  private final ZalavaToolApprovalRequests approvals;
  private final ToolArgumentDecoder decoder;

  public ConversationEvidenceQueries(
      ActorChatQueries conversations,
      AgentRunQueries runs,
      InvocationLogQueries tools,
      ModelBoundary boundary,
      ZalavaToolApprovalRequests approvals,
      ToolArgumentDecoder decoder) {
    this.conversations = conversations;
    this.runs = runs;
    this.tools = tools;
    this.boundary = boundary;
    this.approvals = approvals;
    this.decoder = decoder;
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
    List<Tool> evidence =
        new ArrayList<>(
            tools.recentEntries().stream()
                .filter(
                    tool ->
                        internalId.equals(tool.conversationId())
                            && actor.accountId().toString().equals(tool.actorId()))
                .limit(50)
                .map(
                    tool -> {
                      String approvalId = approvalId(tool);
                      if (approvalId != null) observedApprovals.add(approvalId);
                      String approvalStatus = approvalState(tool, actor, internalId);
                      return new Tool(
                          tool.timestamp(),
                          tool.providerId(),
                          tool.toolName(),
                          tool.success(),
                          tool.durationMillis(),
                          boundary.redact(tool.errorMessage()),
                          boundary.redact(tool.resultPreview()),
                          boundary.redact(tool.inputPreview()),
                          tool.runId(),
                          "pending_approval".equals(tool.errorType())
                              && approvalStatus.equals("pending"),
                          approvalStatus);
                    })
                .toList());
    approvals.recentEntries().stream()
        .filter(entry -> "home-assistant".equals(entry.providerId()))
        .filter(entry -> "home_assistant_execute_action".equals(entry.toolName()))
        .filter(entry -> actor.accountId().toString().equals(entry.actorId()))
        .filter(entry -> internalId.equals(entry.attributes().get("conversationId")))
        .filter(entry -> "web".equals(entry.attributes().get("permissionPolicyChannel")))
        .filter(
            entry ->
                !entry.attributes().getOrDefault(InvocationChannelContext.RUN_ID, "").isBlank())
        .filter(entry -> !observedApprovals.contains(entry.requestId()))
        .map(this::retainedHomeAction)
        .forEach(evidence::add);
    return evidence.stream().sorted(Comparator.comparing(Tool::at).reversed()).limit(50).toList();
  }

  private Tool retainedHomeAction(ZalavaToolApprovalRequests.Entry entry) {
    String state = approvalStatus(entry);
    return new Tool(
        entry.createdAt(),
        entry.providerId(),
        entry.toolName(),
        "succeeded".equals(state),
        0,
        null,
        "Retained approval outcome",
        homeActionSummary(entry),
        entry.attributes().get(InvocationChannelContext.RUN_ID),
        "pending".equals(state),
        state);
  }

  private static String homeActionSummary(ZalavaToolApprovalRequests.Entry entry) {
    var arguments = entry.arguments();
    if (arguments == null) return "Action details unavailable";
    String domain = arguments.path("domain").asString("");
    String action = arguments.path("action").asString("");
    String entity = arguments.path("entityId").asString("");
    if (!domain.matches("[a-z0-9_]{1,128}")
        || !action.matches("[a-z0-9_]{1,128}")
        || !entity.matches("[a-z0-9_]{1,128}\\.[a-z0-9_]{1,128}")) {
      return "Action details unavailable";
    }
    return "Action: "
        + domain
        + "."
        + action
        + "\nTarget: "
        + entity
        + "\nOther parameters omitted";
  }

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
      long durationMillis,
      String error,
      String result,
      String input,
      String runId,
      boolean pendingApproval,
      String approvalStatus) {}
}
