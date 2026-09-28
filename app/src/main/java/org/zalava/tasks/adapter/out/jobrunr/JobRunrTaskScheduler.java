package org.zalava.tasks.adapter.out.jobrunr;

import java.time.LocalDateTime;
import java.util.List;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.Paging;
import org.jobrunr.storage.StorageProvider;
import org.springframework.stereotype.Component;
import org.zalava.tasks.adapter.in.jobrunr.RecurringTaskHandler;
import org.zalava.tasks.adapter.in.jobrunr.TaskHandler;
import org.zalava.tasks.application.port.out.TaskScheduler;

@Component
public class JobRunrTaskScheduler implements TaskScheduler {

  private final JobScheduler jobScheduler;
  private final StorageProvider storageProvider;

  public JobRunrTaskScheduler(JobScheduler jobScheduler, StorageProvider storageProvider) {
    this.jobScheduler = jobScheduler;
    this.storageProvider = storageProvider;
  }

  @Override
  public void enqueue(String taskId) {
    jobScheduler.<TaskHandler>enqueue(handler -> handler.executeTask(taskId, JobContext.Null));
  }

  @Override
  public void schedule(LocalDateTime executionTime, String taskId) {
    jobScheduler.<TaskHandler>schedule(
        executionTime, handler -> handler.executeTask(taskId, JobContext.Null));
  }

  @Override
  public void scheduleRecurring(
      String recurringTaskName, String cronExpression, String recurringTaskId) {
    jobScheduler.<RecurringTaskHandler>scheduleRecurrently(
        recurringTaskName, cronExpression, handler -> handler.executeTask(recurringTaskId));
  }

  @Override
  public void deleteRecurring(String recurringTaskName) {
    jobScheduler.deleteRecurringJob(recurringTaskName);
    List<Job> scheduledJobs =
        storageProvider.getJobList(
            StateName.SCHEDULED, Paging.AmountBasedList.ascOnUpdatedAt(1000));
    scheduledJobs.stream()
        .filter(job -> job.getRecurringJobId().map(recurringTaskName::equals).orElse(false))
        .map(Job::getId)
        .findFirst()
        .ifPresent(jobScheduler::delete);
  }
}
