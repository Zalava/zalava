package org.zalava.assistant.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.out.ActorTaskApprovalDecisions;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

class UiExecutionStateQueriesTest {
  private final ActorTaskCommands tasks = Mockito.mock(ActorTaskCommands.class);
  private final ActorTaskApprovalDecisions approvals =
      Mockito.mock(ActorTaskApprovalDecisions.class);
  private final UiExecutionStateQueries queries = new UiExecutionStateQueries(tasks, approvals);

  @Test
  void exposesOnlyOwnerScopedBoundedExecutionAndPermissionState() {
    Actor actor = new Actor(AccountId.newId());
    ActorTaskReference reference = ActorTaskReference.newReference();
    Task task =
        new Task(
            reference.value(),
            "Write report",
            Instant.now(),
            Task.Status.awaiting_human_input,
            "Write the report");
    when(tasks.list(actor)).thenReturn(List.of(reference));
    when(tasks.get(actor, reference)).thenReturn(task);
    when(approvals.pendingFor(actor, reference))
        .thenReturn(
            List.of(
                new ActorTaskApprovalDecisions.PendingApproval(
                    "request-1", "files", "write", "Write report", "write", "…", null, null)));

    assertThat(queries.states(actor))
        .containsExactly(
            new UiExecutionStateQueries.ExecutionState(
                reference.value(),
                "awaiting_human_input",
                "Write report",
                List.of(new UiExecutionStateQueries.PendingApproval("request-1", "files/write"))));
  }
}
