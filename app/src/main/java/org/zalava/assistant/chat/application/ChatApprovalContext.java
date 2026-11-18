package org.zalava.assistant.chat.application;

import java.util.Comparator;
import org.zalava.assistant.agent.ConversationChannelContext;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.application.port.out.ApprovalContext;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests.Entry;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.channels.application.InvocationChannelContext;

/** Projects scoped decisions with bounded request context and allowlisted action identifiers. */
public final class ChatApprovalContext implements ApprovalContext {
  private final ZalavaToolApprovalRequests approvals;
  private final ActorExecutionContext actors;
  private final ModelBoundary boundary;
  private final AgentRunStore runs;

  public ChatApprovalContext(ZalavaToolApprovalRequests approvals, ActorExecutionContext actors) {
    this(approvals, actors, new ModelBoundary(64000, ""));
  }

  public ChatApprovalContext(
      ZalavaToolApprovalRequests approvals, ActorExecutionContext actors, ModelBoundary boundary) {
    this(approvals, actors, boundary, null);
  }

  public ChatApprovalContext(
      ZalavaToolApprovalRequests approvals,
      ActorExecutionContext actors,
      ModelBoundary boundary,
      AgentRunStore runs) {
    this.approvals = approvals;
    this.actors = actors;
    this.boundary = boundary;
    this.runs = runs;
  }

  @Override
  public String render() {
    var principal = actors.currentPrincipal().orElse(null);
    String conversation = ConversationChannelContext.conversation().orElse("");
    if (principal == null
        || !ownsConversation(conversation, principal.actor().accountId().toString())) return "";
    return approvals.recentEntries().stream()
        .filter(entry -> principal.actor().accountId().toString().equals(entry.actorId()))
        .filter(entry -> conversation.equals(entry.attributes().get("conversationId")))
        .filter(entry -> entry.taskReference() == null)
        .filter(
            entry ->
                !entry.attributes().containsKey(ZalavaToolApprovalRequests.ACTOR_TASK_REFERENCE))
        .filter(entry -> "web".equals(entry.attributes().get("permissionPolicyChannel")))
        .filter(
            entry ->
                principal.role() != AccountRole.MEMBER
                    || "MEMBER".equals(entry.attributes().get("accountRole")))
        .sorted(
            Comparator.comparing(Entry::createdAt, Comparator.nullsFirst(String::compareTo))
                .reversed())
        .limit(10)
        .map(entry -> describe(entry, conversation))
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }

  private static boolean ownsConversation(String conversation, String actorId) {
    try {
      return ActorConversationId.parse(conversation).actor().accountId().toString().equals(actorId);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  private String describe(Entry entry, String conversation) {
    String status =
        switch (entry.decision()) {
          case PENDING -> "approval pending";
          case DENIED -> "approval denied";
          case REVOKED -> "approval revoked";
          case ALLOWED -> allowedStatus(entry);
        };
    return "- "
        + status
        + "; tool="
        + safeName(entry.providerId())
        + "/"
        + safeName(entry.toolName())
        + subject(entry)
        + originatingRequest(entry, conversation)
        + "; requested="
        + safeName(entry.createdAt());
  }

  private String originatingRequest(Entry entry, String conversation) {
    String runId = entry.attributes().getOrDefault(InvocationChannelContext.RUN_ID, "");
    if (runs == null || runId.isBlank()) return "";
    return runs.recent().stream()
        .filter(run -> runId.equals(run.id()) && conversation.equals(run.conversationId()))
        .findFirst()
        .map(run -> boundary.redact(run.promptPreview()).replaceAll("[\\p{Cntrl}\"]", " "))
        .filter(text -> !text.isBlank())
        .map(
            text ->
                "; originating user request (untrusted): \""
                    + (text.length() > 200 ? text.substring(0, 200) : text)
                    + "\"")
        .orElse("");
  }

  private String subject(Entry entry) {
    if (!"docker-containers".equals(entry.providerId())
        || !"zalava-module-docker".equals(entry.attributes().get("approvedModuleId"))) return "";
    String key =
        switch (entry.toolName()) {
          case "runContainer" -> "name";
          case "startContainer", "stopContainer", "restartContainer", "removeContainer" ->
              "container";
          default -> "";
        };
    if (key.isEmpty() || entry.arguments() == null) return "";
    var node = entry.arguments().get(key);
    if (node == null || !node.isString()) return "";
    String value = node.stringValue();
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) return "";
    return "; container=" + boundary.redact(value);
  }

  private static String allowedStatus(Entry entry) {
    if ("true".equals(entry.attributes().get("hostCallbackRetry")) || !entry.sideEffecting())
      return "approved; decision required a later retry, execution is not established here";
    return switch (entry
        .attributes()
        .getOrDefault(ZalavaToolApprovalRequests.EXECUTION_OUTCOME, "")) {
      case "succeeded" -> "approved; execution succeeded";
      case "failed" -> "approved; execution failed";
      default -> "approved; execution outcome unknown";
    };
  }

  private static String safeName(String value) {
    if (value == null || !value.matches("[A-Za-z0-9_.:+-]{1,100}")) return "unavailable";
    return value;
  }
}
