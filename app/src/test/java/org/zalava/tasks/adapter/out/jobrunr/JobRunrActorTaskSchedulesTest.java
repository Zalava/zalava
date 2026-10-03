package org.zalava.tasks.adapter.out.jobrunr;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.JobParameter;
import org.jobrunr.jobs.states.EnqueuedState;
import org.jobrunr.jobs.states.ScheduledState;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.navigation.OffsetBasedPageRequest;
import org.jobrunr.storage.navigation.PageRequest;
import org.junit.jupiter.api.Test;
import org.zalava.identity.accounts.domain.*;
import org.zalava.tasks.adapter.in.jobrunr.TaskHandler;
import org.zalava.tasks.domain.*;

class JobRunrActorTaskSchedulesTest {
  final Actor owner = new Actor(AccountId.newId());
  final Actor other = new Actor(AccountId.newId());
  final StorageProvider storage = mock(StorageProvider.class);

  @Test
  void readsEveryPageAndFiltersOtherActorsUnrelatedMalformedAndUnscheduledJobs() {
    var reference = ActorTaskReference.newReference();
    Instant due = Instant.parse("2050-01-02T12:00:00Z");
    var ours = job(new ActorTaskExecutionReference(owner, reference).encode(), due);
    var foreign =
        IntStream.range(0, 100)
            .mapToObj(
                i ->
                    job(
                        new ActorTaskExecutionReference(other, ActorTaskReference.newReference())
                            .encode(),
                        due))
            .toList();
    var malformed = job("not-an-owner-token", due);
    var unrelated =
        new Job(
            new JobDetails(
                "other.Handler",
                null,
                "executeTask",
                List.of(
                    new JobParameter(
                        String.class, new ActorTaskExecutionReference(owner, reference).encode()))),
            new ScheduledState(due));
    var enqueued = new Job(ours.getJobDetails(), new EnqueuedState());
    when(storage.getJobs(eq(StateName.SCHEDULED), any(PageRequest.class)))
        .thenAnswer(
            call -> {
              var page = (OffsetBasedPageRequest) call.getArgument(1);
              return page.mapToNewPage(
                  104,
                  page.getOffset() == 0 ? foreign : List.of(ours, malformed, unrelated, enqueued));
            });
    assertThat(new JobRunrActorTaskSchedules(storage).list(owner))
        .containsExactly(new ScheduledActorTask(ours.getId(), reference, due));
    verify(storage, times(2)).getJobs(eq(StateName.SCHEDULED), any(PageRequest.class));
  }

  @Test
  void readsUpdatedScheduleStateAndDoesNotRetainRemovedSchedules() {
    var reference = ActorTaskReference.newReference();
    var first =
        job(
            new ActorTaskExecutionReference(owner, reference).encode(),
            Instant.parse("2050-01-01T12:00:00Z"));
    when(storage.getJobs(eq(StateName.SCHEDULED), any(PageRequest.class)))
        .thenAnswer(
            call -> {
              var page = (OffsetBasedPageRequest) call.getArgument(1);
              return page.mapToNewPage(1, List.of(first));
            });
    var adapter = new JobRunrActorTaskSchedules(storage);
    assertThat(adapter.list(owner).getFirst().scheduledAt())
        .isEqualTo(Instant.parse("2050-01-01T12:00:00Z"));
    first.scheduleAt(Instant.parse("2050-01-03T12:00:00Z"), "Changed");
    assertThat(adapter.list(owner).getFirst().scheduledAt())
        .isEqualTo(Instant.parse("2050-01-03T12:00:00Z"));
    when(storage.getJobs(eq(StateName.SCHEDULED), any(PageRequest.class)))
        .thenAnswer(call -> ((OffsetBasedPageRequest) call.getArgument(1)).emptyPage());
    assertThat(adapter.list(owner)).isEmpty();
  }

  @Test
  void reportsPersistenceFailureRatherThanAnEmptySchedule() {
    when(storage.getJobs(eq(StateName.SCHEDULED), any(PageRequest.class)))
        .thenThrow(new IllegalStateException("Unavailable"));
    assertThatThrownBy(() -> new JobRunrActorTaskSchedules(storage).list(owner))
        .isInstanceOf(TaskScheduleUnavailableException.class)
        .hasRootCauseMessage("Unavailable");
  }

  private static Job job(String token, Instant due) {
    return new Job(
        UUID.randomUUID(),
        new JobDetails(
            TaskHandler.class.getName(),
            null,
            "executeTask",
            List.of(new JobParameter(String.class, token))),
        new ScheduledState(due));
  }
}
