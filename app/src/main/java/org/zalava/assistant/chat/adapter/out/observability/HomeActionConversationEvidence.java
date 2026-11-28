package org.zalava.assistant.chat.adapter.out.observability;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.zalava.assistant.chat.application.ConversationEvidenceQueries.Tool;
import org.zalava.assistant.chat.application.port.out.ConversationEvidenceSupplement;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.InvocationChannelContext;

/** Adapts the existing Home Assistant-specific retained evidence into generic Chat evidence. */
@Component
public final class HomeActionConversationEvidence implements ConversationEvidenceSupplement {
  private final ZalavaToolApprovalRequests approvals;
  private final StandingHomeActionEvidenceStore standing;

  public HomeActionConversationEvidence(
      ZalavaToolApprovalRequests approvals, StandingHomeActionEvidenceStore standing) {
    this.approvals = approvals;
    this.standing = standing;
  }

  @Override
  public List<Tool> retainedApprovals(Actor actor, String conversationId, Set<String> observed) {
    return approvals.recentEntries().stream()
        .filter(entry -> "home-assistant".equals(entry.providerId()))
        .filter(entry -> "home_assistant_execute_action".equals(entry.toolName()))
        .filter(entry -> actor.accountId().toString().equals(entry.actorId()))
        .filter(entry -> conversationId.equals(entry.attributes().get("conversationId")))
        .filter(entry -> "web".equals(entry.attributes().get("permissionPolicyChannel")))
        .filter(
            entry ->
                !entry.attributes().getOrDefault(InvocationChannelContext.RUN_ID, "").isBlank())
        .filter(entry -> !observed.contains(entry.requestId()))
        .map(this::approval)
        .toList();
  }

  @Override
  public List<Execution> executions(Actor actor, String conversationId) {
    return standing.recent(actor, conversationId).stream()
        .map(
            execution -> {
              String outcome = execution.success() ? "accepted" : "unknown";
              var tool =
                  new Tool(
                      execution.timestamp(),
                      "home-assistant",
                      "home_assistant_execute_action",
                      execution.success(),
                      execution.durationMillis(),
                      null,
                      execution.success()
                          ? "Home Assistant accepted the action; the physical outcome was not verified."
                          : "The action outcome could not be verified. Check its state before retrying.",
                      summary(execution.domain(), execution.action(), execution.entityId()),
                      execution.runId(),
                      false,
                      "",
                      outcome);
              return new Execution(execution.executionId(), outcome, tool);
            })
        .toList();
  }

  private Tool approval(ZalavaToolApprovalRequests.Entry entry) {
    String state =
        switch (entry.decision()) {
          case PENDING -> "pending";
          case DENIED, REVOKED -> "denied";
          case ALLOWED ->
              entry
                  .attributes()
                  .getOrDefault(ZalavaToolApprovalRequests.EXECUTION_OUTCOME, "unknown");
        };
    var arguments = entry.arguments();
    String input = "Action details unavailable";
    if (arguments != null) {
      String domain = arguments.path("domain").asString("");
      String action = arguments.path("action").asString("");
      String entity = arguments.path("entityId").asString("");
      if (domain.matches("[a-z0-9_]{1,128}")
          && action.matches("[a-z0-9_]{1,128}")
          && entity.matches("[a-z0-9_]{1,128}\\.[a-z0-9_]{1,128}")) {
        input = summary(domain, action, entity);
      }
    }
    return new Tool(
        entry.createdAt(),
        entry.providerId(),
        entry.toolName(),
        "succeeded".equals(state),
        null,
        null,
        "Retained approval outcome",
        input,
        entry.attributes().get(InvocationChannelContext.RUN_ID),
        "pending".equals(state),
        state,
        null);
  }

  private static String summary(String domain, String action, String entity) {
    return "Action: "
        + domain
        + "."
        + action
        + "\nTarget: "
        + entity
        + "\nOther parameters omitted";
  }
}
