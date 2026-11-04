package org.zalava.assistant.chat.application;

import java.util.List;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;

/** Owns actor-scoped direct Chat approval decisions independently of the UI transport. */
public final class DirectChatApprovals {
  private final ZalavaToolApprovalRequests approvals;
  private final ProviderToolOperations operations;

  public DirectChatApprovals(
      ZalavaToolApprovalRequests approvals, ProviderToolOperations operations) {
    this.approvals = approvals;
    this.operations = operations;
  }

  public List<ZalavaToolApprovalRequests.Entry> pending(Actor actor, AccountRole role) {
    return approvals.recentEntries().stream()
        .filter(entry -> directFor(entry, actor, role))
        .filter(entry -> entry.decision() == ZalavaToolApprovalRequests.Decision.PENDING)
        .toList();
  }

  public String decide(Actor actor, AccountRole role, String requestId, boolean allow) {
    var entry = approvals.get(requestId);
    if (!directFor(entry, actor, role))
      throw new ZalavaToolApprovalRequests.NotFoundException(requestId);
    if (!allow) {
      operations.denyUnscoped(requestId);
      return "Permission denied; operation was not run.";
    }
    if ("true".equals(entry.attributes().get("hostCallbackRetry"))) {
      approvals.approveChatHostOperation(requestId);
      return "Action approved. Ask the same question again in this conversation within five minutes.";
    }
    if (!entry.sideEffecting()) {
      if (entry.attributes().getOrDefault("conversationId", "").isBlank())
        throw new IllegalStateException(
            "This read request cannot be continued safely; please deny it and try again.");
      operations.approveChatRead(requestId);
      return "Read approved. Ask the same question again in this conversation within five minutes.";
    }
    var outcome = operations.allowUnscoped(requestId);
    return outcome.result().success()
        ? "Permission allowed once; operation completed."
        : "Permission allowed once, but the operation returned an error. Check the result before retrying.";
  }

  private static boolean directFor(
      ZalavaToolApprovalRequests.Entry entry, Actor actor, AccountRole role) {
    return actor.accountId().toString().equals(entry.actorId())
        && entry.taskReference() == null
        && !entry.attributes().containsKey(ZalavaToolApprovalRequests.ACTOR_TASK_REFERENCE)
        && "web".equals(entry.attributes().get("permissionPolicyChannel"))
        && (role != AccountRole.MEMBER || "MEMBER".equals(entry.attributes().get("accountRole")));
  }
}
