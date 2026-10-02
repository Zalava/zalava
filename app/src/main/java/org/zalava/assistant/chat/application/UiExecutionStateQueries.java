package org.zalava.assistant.chat.application;

import java.util.List;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.out.ActorTaskApprovalDecisions;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

/** Provides bounded, owner-scoped execution state for an interactive UI adapter. */
public final class UiExecutionStateQueries {
  private final ActorTaskCommands tasks;
  private final ActorTaskApprovalDecisions approvals;

  public UiExecutionStateQueries(ActorTaskCommands tasks, ActorTaskApprovalDecisions approvals) {
    this.tasks = tasks;
    this.approvals = approvals;
  }

  public List<ExecutionState> states(Actor actor) {
    return tasks.list(actor).stream().map(reference -> state(actor, reference)).toList();
  }

  public ExecutionState state(Actor actor, ActorTaskReference taskReference) {
    Task task = tasks.get(actor, taskReference);
    List<PendingApproval> pending =
        approvals.pendingFor(actor, taskReference).stream()
            .map(
                approval ->
                    new PendingApproval(
                        approval.requestId(), approval.providerId() + "/" + approval.toolName()))
            .toList();
    return new ExecutionState(
        taskReference.value(), task.getStatus().name(), task.getName(), pending);
  }

  public record ExecutionState(
      String jobId, String status, String summary, List<PendingApproval> pendingApprovals) {
    public ExecutionState {
      pendingApprovals = List.copyOf(pendingApprovals);
    }
  }

  public record PendingApproval(String requestId, String summary) {}
}
