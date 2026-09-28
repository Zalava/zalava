package org.zalava.chat.ws;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.operation.application.port.in.ProviderToolOperations;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.ui.protocol.UiCommand;

class UiApprovalDecisionsTest {
  private final AuthenticatedActorResolver actors = Mockito.mock(AuthenticatedActorResolver.class);
  private final ActorTaskCommands tasks = Mockito.mock(ActorTaskCommands.class);
  private final SeaToolApprovalRequests approvals = Mockito.mock(SeaToolApprovalRequests.class);
  private final ProviderToolOperations operations = Mockito.mock(ProviderToolOperations.class);
  private final UiApprovalDecisions decisions =
      new UiApprovalDecisions(actors, tasks, approvals, operations);

  @Test
  void ownerCanAllowOnceAndResumeTheirAwaitingJob() {
    Fixture fixture = fixture();

    decisions.decide(
        "member",
        new UiCommand.DecideApproval(
            "sea.ui/v1",
            fixture.reference().value(),
            "request-1",
            UiCommand.DecideApproval.Decision.ALLOW_ONCE));

    verify(operations).allowUnscoped("request-1");
    verify(tasks).resume(fixture.actor(), fixture.reference());
  }

  @Test
  void ownerCanDenyTheirAwaitingJob() {
    Fixture fixture = fixture();

    decisions.decide(
        "member",
        new UiCommand.DecideApproval(
            "sea.ui/v1",
            fixture.reference().value(),
            "request-1",
            UiCommand.DecideApproval.Decision.DENY));

    verify(operations).denyUnscoped("request-1");
  }

  private Fixture fixture() {
    Actor actor = new Actor(AccountId.newId());
    ActorTaskReference reference = ActorTaskReference.newReference();
    Task task =
        new Task(
            reference.value(),
            "Write report",
            Instant.now(),
            Task.Status.awaiting_human_input,
            "Write");
    SeaToolApprovalRequests.Entry approval = Mockito.mock(SeaToolApprovalRequests.Entry.class);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(actors.roleForLogin("member")).thenReturn(AccountRole.MEMBER);
    when(tasks.get(actor, reference)).thenReturn(task);
    when(approvals.get(actor, reference, "request-1")).thenReturn(approval);
    when(approval.attributes()).thenReturn(Map.of("accountRole", "MEMBER"));
    when(approvals.hasPending(actor, reference)).thenReturn(false);
    return new Fixture(actor, reference);
  }

  private record Fixture(Actor actor, ActorTaskReference reference) {}
}
