package org.zalava.tasks.adapter.out.jobrunr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.ScheduledState;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.Paging;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.navigation.OffsetBasedPageRequest;
import org.springframework.stereotype.Component;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.adapter.in.jobrunr.TaskHandler;
import org.zalava.tasks.application.port.out.ActorTaskSchedules;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ScheduledActorTask;
import org.zalava.tasks.domain.TaskScheduleUnavailableException;

@Component
public final class JobRunrActorTaskSchedules implements ActorTaskSchedules {
  private static final int PAGE_SIZE = 100;
  private final StorageProvider storage;

  public JobRunrActorTaskSchedules(StorageProvider storage) {
    this.storage = storage;
  }

  @Override
  public List<ScheduledActorTask> list(Actor actor) {
    var result = new ArrayList<ScheduledActorTask>();
    long offset = 0;
    while (true) {
      org.jobrunr.storage.Page<Job> page;
      try {
        page =
            storage.getJobs(
                StateName.SCHEDULED,
                new OffsetBasedPageRequest(
                    Paging.AmountBasedList.ascOnScheduledAt(PAGE_SIZE).getOrder(),
                    offset,
                    PAGE_SIZE));
      } catch (RuntimeException exception) {
        throw new TaskScheduleUnavailableException(exception);
      }
      for (Job job : page.getItems()) {
        var details = job.getJobDetails();
        if (!TaskHandler.class.getName().equals(details.getClassName())
            || !"executeTask".equals(details.getMethodName())
            || !(job.getJobState() instanceof ScheduledState state)) continue;
        Object[] values = details.getJobParameterValues();
        if (values.length == 0 || !(values[0] instanceof String token)) continue;
        ActorTaskExecutionReference execution;
        try {
          execution = ActorTaskExecutionReference.parse(token);
        } catch (IllegalArgumentException malformed) {
          continue;
        }
        if (actor.equals(execution.actor()))
          result.add(
              new ScheduledActorTask(
                  job.getId(), execution.taskReference(), state.getScheduledAt()));
      }
      if (!page.hasNextPage()) break;
      offset += page.getItems().size();
      if (page.getItems().isEmpty()) break;
    }
    return result.stream().sorted(Comparator.comparing(ScheduledActorTask::scheduledAt)).toList();
  }
}
