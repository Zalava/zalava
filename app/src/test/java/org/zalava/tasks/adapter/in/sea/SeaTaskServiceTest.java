package org.zalava.tasks.adapter.in.sea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.zalava.InvocationContext;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskReference;
import org.zalava.tools.ActorTaskCreationContext;
import org.junit.jupiter.api.Test;

class SeaTaskServiceTest {
  @Test
  void delegatesCreateToTheHostTaskCommands() {
    TaskCommands commands = mock(TaskCommands.class);
    when(commands.create("research", "Investigate module SPI"))
        .thenReturn(new TaskReference(LocalDate.of(2026, 8, 16), "123456-research.md"));
    SeaTaskService service = new SeaTaskService(commands, mock(TaskQueries.class));

    var result =
        service.create(
            new InvocationContext("operator", true, java.util.Map.of()),
            "research",
            "Investigate module SPI");

    assertThat(result.status()).isEqualTo("created");
    assertThat(result.taskReference().value()).isEqualTo("2026-08-16/123456-research.md");
  }

  @Test
  void rejectsCallsWithoutAnActor() {
    SeaTaskService service = new SeaTaskService(mock(TaskCommands.class), mock(TaskQueries.class));

    assertThatThrownBy(
            () -> service.listRecurring(new InvocationContext(" ", false, java.util.Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Task service requires an actor");
  }

  @Test
  void createsAndCapturesAnOpaqueActorOwnedTaskForProductExecution() {
    Actor actor = new Actor(AccountId.newId());
    ActorTaskReference reference = ActorTaskReference.newReference();
    ActorTaskCommands actorCommands = mock(ActorTaskCommands.class);
    ActorTaskCreationContext capture = new ActorTaskCreationContext();
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    when(actorCommands.create(actor, "research", "private goal")).thenReturn(reference);
    SeaTaskService service =
        new SeaTaskService(
            mock(TaskCommands.class),
            mock(TaskQueries.class),
            actorCommands,
            capture,
            actorExecution);

    var result =
        actorExecution.call(
            actor,
            AccountRole.MEMBER,
            () ->
                capture.capture(
                    () ->
                        service.create(
                            new InvocationContext(
                                AccountId.newId().toString(),
                                false,
                                java.util.Map.of("accountRole", "MEMBER")),
                            "research",
                            "private goal")));

    assertThat(result.value().taskReference().value()).isEqualTo(reference.value());
    assertThat(result.taskReferences()).containsExactly(reference);
    verify(actorCommands).create(actor, "research", "private goal");
  }
}
