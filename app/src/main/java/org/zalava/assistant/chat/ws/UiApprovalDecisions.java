package org.zalava.assistant.chat.ws;

import org.springframework.stereotype.Component;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.web.ui.protocol.UiCommand;

/** Applies a UI approval intent through the existing owner-scoped Zalava authority. */
@Component
final class UiApprovalDecisions {
  private final AuthenticatedActorResolver actors;
  private final ActorTaskCommands tasks;
  private final ZalavaToolApprovalRequests approvals;
  private final ProviderToolOperations operations;

  UiApprovalDecisions(
      AuthenticatedActorResolver actors,
      ActorTaskCommands tasks,
      ZalavaToolApprovalRequests approvals,
      ProviderToolOperations operations) {
    this.actors = actors;
    this.tasks = tasks;
    this.approvals = approvals;
    this.operations = operations;
  }

  Task decide(String loginName, UiCommand.DecideApproval command) {
    Actor actor = actors.actorForLogin(loginName);
    ActorTaskReference taskReference = new ActorTaskReference(command.jobId());
    Task task = tasks.get(actor, taskReference);
    if (task.getStatus() != Task.Status.awaiting_human_input) {
      throw new IllegalStateException("Job is not waiting for an approval decision");
    }
    ZalavaToolApprovalRequests.Entry approval =
        approvals.get(actor, taskReference, command.requestId());
    if (actors.roleForLogin(loginName) == AccountRole.MEMBER
        && !"MEMBER".equals(approval.attributes().get("accountRole"))) {
      throw new ZalavaToolApprovalRequests.NotFoundException(command.requestId());
    }
    switch (command.decision()) {
      case ALLOW_ONCE -> operations.allowUnscoped(command.requestId());
      case DENY -> operations.denyUnscoped(command.requestId());
    }
    if (!approvals.hasPending(actor, taskReference)) {
      tasks.resume(actor, taskReference);
    }
    return tasks.get(actor, taskReference);
  }
}
