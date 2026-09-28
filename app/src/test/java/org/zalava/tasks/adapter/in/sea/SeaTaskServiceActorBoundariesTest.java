package org.zalava.tasks.adapter.in.sea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.zalava.InvocationContext;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.RecurringTaskSummary;
import org.zalava.tasks.TaskServiceResult;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.TaskReference;
import org.zalava.tools.ActorTaskCreationContext;
import org.junit.jupiter.api.Test;

class SeaTaskServiceActorBoundariesTest {

  private static InvocationContext contextFor(Actor actor, boolean legacyPrincipal) {
    return new InvocationContext(
        actor.accountId().toString(),
        false,
        Map.of("accountRole", legacyPrincipal ? "ADMIN" : "MEMBER"));
  }

  @Test
  void actorScopedCreateRoutesThroughTheActorCommandsAndNotifiesConsumers() {
    Actor actor = new Actor(AccountId.newId());
    ActorTaskReference reference = ActorTaskReference.newReference();
    ActorTaskCommands actorCommands = mock(ActorTaskCommands.class);
    ActorTaskCreationContext creation = new ActorTaskCreationContext();
    ActorExecutionContext execution = new ActorExecutionContext();
    when(actorCommands.create(actor, "research", "goal")).thenReturn(reference);
    SeaTaskService service =
        new SeaTaskService(
            mock(TaskCommands.class), mock(TaskQueries.class), actorCommands, creation, execution);

    var captured =
        execution.call(
            actor,
            AccountRole.MEMBER,
            () ->
                creation.capture(
                    () -> service.create(contextFor(actor, false), "research", "goal")));
    TaskServiceResult result = captured.value();

    assertThat(result.status()).isEqualTo("created");
    assertThat(result.taskReference().value()).isEqualTo(reference.value());
    verify(actorCommands).create(actor, "research", "goal");
  }

  @Test
  void actorScopedScheduleRoutesThroughTheActorCommands() {
    Actor actor = new Actor(AccountId.newId());
    ActorTaskReference reference = ActorTaskReference.newReference();
    ActorTaskCommands actorCommands = mock(ActorTaskCommands.class);
    ActorTaskCreationContext creation = new ActorTaskCreationContext();
    ActorExecutionContext execution = new ActorExecutionContext();
    when(actorCommands.schedule(actor, LocalDateTime.parse("2026-09-05T10:00"), "remind", "goal"))
        .thenReturn(reference);
    SeaTaskService service =
        new SeaTaskService(
            mock(TaskCommands.class), mock(TaskQueries.class), actorCommands, creation, execution);

    var captured =
        execution.call(
            actor,
            AccountRole.MEMBER,
            () ->
                creation.capture(
                    () ->
                        service.schedule(
                            contextFor(actor, false), "2026-09-05T10:00", "remind", "goal")));
    TaskServiceResult result = captured.value();

    assertThat(result.status()).isEqualTo("scheduled");
    assertThat(result.taskReference().value()).isEqualTo(reference.value());
  }

  @Test
  void legacyPrincipalsFallBackToHostTaskCommands() {
    TaskCommands commands = mock(TaskCommands.class);
    TaskQueries queries = mock(TaskQueries.class);
    ActorTaskCommands actorCommands = mock(ActorTaskCommands.class);
    ActorTaskCreationContext creation = new ActorTaskCreationContext();
    ActorExecutionContext execution = new ActorExecutionContext();
    Actor actor = new Actor(AccountId.newId());
    TaskReference legacyReference = new TaskReference(LocalDate.of(2026, 9, 2), "100000-legacy.md");
    when(commands.create("legacy", "goal")).thenReturn(legacyReference);
    when(commands.schedule(LocalDateTime.parse("2026-09-06T08:00"), "legacy", "goal"))
        .thenReturn(new TaskReference(LocalDate.of(2026, 9, 6), "080000-legacy.md"));
    SeaTaskService service =
        new SeaTaskService(commands, queries, actorCommands, creation, execution);

    TaskServiceResult created = service.create(contextFor(actor, true), "legacy", "goal");
    TaskServiceResult scheduled =
        service.schedule(contextFor(actor, true), "2026-09-06T08:00", "legacy", "goal");

    assertThat(created.status()).isEqualTo("created");
    assertThat(created.taskReference().value()).isEqualTo("2026-09-02/100000-legacy.md");
    assertThat(scheduled.status()).isEqualTo("scheduled");
    verify(commands).create("legacy", "goal");
    verify(commands).schedule(LocalDateTime.parse("2026-09-06T08:00"), "legacy", "goal");
  }

  @Test
  void recurringOperationsRequireALegacyPrincipal() {
    TaskCommands commands = mock(TaskCommands.class);
    TaskQueries queries = mock(TaskQueries.class);
    ActorExecutionContext execution = new ActorExecutionContext();
    Actor actor = new Actor(AccountId.newId());
    when(queries.getAllRecurringTasks())
        .thenReturn(List.of(new RecurringTask("id-1", "nightly", "Nightly check")));
    SeaTaskService service =
        new SeaTaskService(
            commands,
            queries,
            mock(ActorTaskCommands.class),
            new ActorTaskCreationContext(),
            execution);

    TaskServiceResult scheduled =
        service.scheduleRecurring(
            contextFor(actor, true), "0 0 3 * * *", "nightly", "Nightly check");
    TaskServiceResult deleted = service.deleteRecurring(contextFor(actor, true), "nightly");
    List<RecurringTaskSummary> listed = service.listRecurring(contextFor(actor, true));

    assertThat(scheduled.status()).isEqualTo("recurring_scheduled");
    assertThat(scheduled.recurringTask().name()).isEqualTo("nightly");
    assertThat(deleted.status()).isEqualTo("recurring_deleted");
    assertThat(deleted.recurringTask().id()).isEqualTo("id-1");
    assertThat(listed).hasSize(1);
    verify(commands).scheduleRecurrently("0 0 3 * * *", "nightly", "Nightly check");
    verify(commands).deleteRecurringTask("nightly");
  }

  @Test
  void recurringOperationsRejectActorPrincipals() {
    Actor actor = new Actor(AccountId.newId());
    ActorExecutionContext execution = new ActorExecutionContext();
    SeaTaskService service =
        new SeaTaskService(
            mock(TaskCommands.class),
            mock(TaskQueries.class),
            mock(ActorTaskCommands.class),
            new ActorTaskCreationContext(),
            execution);

    assertThatThrownBy(
            () ->
                execution.call(
                    actor,
                    AccountRole.MEMBER,
                    () ->
                        service.scheduleRecurring(
                            contextFor(actor, false), "0 0 3 * * *", "nightly", "Nightly check")))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Actor-scoped recurring jobs are not available");
  }

  @Test
  void unconfiguredActorDependenciesFailClosedWhenAPrincipalIsPresent() {
    Actor actor = new Actor(AccountId.newId());
    ActorExecutionContext execution = new ActorExecutionContext();
    SeaTaskService service =
        new SeaTaskService(
            mock(TaskCommands.class), mock(TaskQueries.class), null, null, execution);

    assertThatThrownBy(
            () ->
                execution.call(
                    actor,
                    AccountRole.MEMBER,
                    () -> service.create(contextFor(actor, false), "research", "goal")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Actor task service is not configured");
  }

  @Test
  void findRecurringFailsWhenTheNameIsUnknown() {
    TaskQueries queries = mock(TaskQueries.class);
    when(queries.getAllRecurringTasks()).thenReturn(List.of());
    SeaTaskService service =
        new SeaTaskService(
            mock(TaskCommands.class),
            queries,
            mock(ActorTaskCommands.class),
            new ActorTaskCreationContext(),
            new ActorExecutionContext());
    Actor actor = new Actor(AccountId.newId());

    assertThatThrownBy(
            () ->
                service.scheduleRecurring(
                    contextFor(actor, true), "0 0 3 * * *", "ghost", "Missing recurring task"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Recurring task with name ghost was not found");
  }

  @Test
  void legacyThreeArgumentConstructorKeepsLegacyBehavior() {
    TaskCommands commands = mock(TaskCommands.class);
    TaskQueries queries = mock(TaskQueries.class);
    TaskReference legacyReference = new TaskReference(LocalDate.of(2026, 9, 2), "110000-legacy.md");
    when(commands.create("legacy", "goal")).thenReturn(legacyReference);
    SeaTaskService service = new SeaTaskService(commands, queries);

    TaskServiceResult result =
        service.create(contextFor(new Actor(AccountId.newId()), true), "legacy", "goal");

    assertThat(result.status()).isEqualTo("created");
    assertThat(result.taskReference().value()).isEqualTo("2026-09-02/110000-legacy.md");
  }

  @Test
  void rejectsAnActorContextWithoutAnIdentifier() {
    SeaTaskService service = new SeaTaskService(mock(TaskCommands.class), mock(TaskQueries.class));

    assertThatThrownBy(
            () -> service.create(new InvocationContext(" ", false, Map.of()), "name", "goal"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Task service requires an actor");
    assertThatThrownBy(() -> service.create(null, "name", "goal"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Task service requires an actor");
  }
}
