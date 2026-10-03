package org.zalava.tasks.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.identity.accounts.domain.*;
import org.zalava.tasks.adapter.out.filesystem.ActorFileSystemTaskStore;
import org.zalava.tasks.application.port.out.ActorTaskSchedules;
import org.zalava.tasks.domain.*;

class DefaultActorJobEvidenceQueriesTest {
  @TempDir Path workspace;
  final Actor owner = new Actor(AccountId.newId());
  final Actor other = new Actor(AccountId.newId());
  final ActorTaskSchedules schedules = mock(ActorTaskSchedules.class);

  @Test
  void readsOnlyOwnedPersistedReportsAndSchedulesAcrossStoreRecreation() {
    var store = new ActorFileSystemTaskStore(workspace);
    var saved = ActorTaskReference.newReference();
    var pending = ActorTaskReference.newReference();
    var absent = ActorTaskReference.newReference();
    Instant created = Instant.parse("2040-01-01T00:00:00Z");
    store.save(
        owner,
        saved,
        new Task(
            null,
            "Saved report",
            created,
            Task.Status.failed,
            "Goal",
            "# Saved output\nExact persisted report.",
            "Tool failed"));
    store.save(owner, pending, Task.newTask("Future work", "Goal"));
    store.save(
        other,
        ActorTaskReference.newReference(),
        new Task(null, "Foreign report", created, Task.Status.completed, "Goal", "Private"));
    when(schedules.list(owner))
        .thenReturn(
            List.of(
                new ScheduledActorTask(UUID.randomUUID(), pending, created.plusSeconds(100)),
                new ScheduledActorTask(UUID.randomUUID(), absent, created.plusSeconds(200))));
    var query =
        new DefaultActorJobEvidenceQueries(new ActorFileSystemTaskStore(workspace), schedules);
    var snapshot = query.snapshot(owner);
    assertThat(snapshot.reports())
        .singleElement()
        .satisfies(
            report -> {
              assertThat(report.name()).isEqualTo("Saved report");
              assertThat(report.status()).isEqualTo(Task.Status.failed);
            });
    assertThat(snapshot.upcoming())
        .singleElement()
        .satisfies(job -> assertThat(job.reference()).isEqualTo(pending));
    assertThat(query.report(owner, saved)).isEqualTo("# Saved output\nExact persisted report.");
    assertThatThrownBy(() -> query.report(other, saved)).isInstanceOf(TaskNotFoundException.class);
    assertThatThrownBy(() -> query.report(owner, pending))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void distinguishesEmptyScheduleAndUnavailableScheduleWhileRetainingRealReports() {
    var store = new ActorFileSystemTaskStore(workspace);
    when(schedules.list(owner)).thenReturn(List.of());
    var query = new DefaultActorJobEvidenceQueries(store, schedules);
    assertThat(query.snapshot(owner).schedulingUnavailable()).isFalse();
    var reference = ActorTaskReference.newReference();
    store.save(owner, reference, Task.newTask("Report", "Goal").withFeedback("Saved"));
    when(schedules.list(owner))
        .thenThrow(new TaskScheduleUnavailableException(new IllegalStateException("Offline")));
    assertThat(query.snapshot(owner).schedulingUnavailable()).isTrue();
    assertThat(query.snapshot(owner).reports()).hasSize(1);
    assertThat(query.snapshot(owner).upcoming()).isEmpty();
  }

  @Test
  void scheduledTaskCreationTimeIsIndependentFromDueTime() {
    var store = new ActorFileSystemTaskStore(workspace);
    var scheduler = mock(org.zalava.tasks.application.port.out.TaskScheduler.class);
    Instant before = Instant.now();
    var due = java.time.LocalDateTime.of(2050, 1, 1, 12, 0);
    var reference = new ActorTaskUseCases(store, scheduler).schedule(owner, due, "Future", "Goal");
    assertThat(store.get(owner, reference).getCreatedAt()).isBetween(before, Instant.now());
    verify(scheduler).schedule(due, new ActorTaskExecutionReference(owner, reference).encode());
  }
}
