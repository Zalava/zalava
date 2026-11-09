package org.zalava.assistant.chat.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.port.in.AgentRunQueries;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.out.ToolArgumentDecoder;
import org.zalava.identity.accounts.domain.Actor;
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
        tools.recentEntries().stream()
            .filter(
                tool ->
                    internalId.equals(tool.conversationId())
                        && actor.accountId().toString().equals(tool.actorId()))
            .limit(50)
            .map(
                tool -> {
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
      return switch (entry.decision()) {
        case PENDING -> "pending";
        case DENIED, REVOKED -> "denied";
        case ALLOWED ->
            entry
                .attributes()
                .getOrDefault(ZalavaToolApprovalRequests.EXECUTION_OUTCOME, "unknown");
      };
    } catch (RuntimeException unavailable) {
      return "unknown";
    }
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
