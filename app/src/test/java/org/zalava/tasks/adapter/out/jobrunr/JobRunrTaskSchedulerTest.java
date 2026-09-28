package org.zalava.tasks.adapter.out.jobrunr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.JobActivator;
import org.jobrunr.server.JobActivatorShutdownException;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.zalava.tasks.adapter.in.jobrunr.RecurringTaskHandler;
import org.zalava.tasks.adapter.in.jobrunr.TaskHandler;

@Isolated
class JobRunrTaskSchedulerTest {

  private final TaskHandler taskHandler = mock(TaskHandler.class);
  private final RecurringTaskHandler recurringTaskHandler = mock(RecurringTaskHandler.class);

  private InMemoryStorageProvider storageProvider;
  private JobRunrTaskScheduler taskScheduler;

  @BeforeEach
  void setUp() {
    storageProvider = new InMemoryStorageProvider();
    JobScheduler jobScheduler =
        JobRunr.configure()
            .useStorageProvider(storageProvider)
            .useJobActivator(jobActivator())
            .useBackgroundJobServer(
                usingStandardBackgroundJobServerConfiguration()
                    .andPollInterval(Duration.ofMillis(200)))
            .initialize()
            .getJobScheduler();
    taskScheduler = new JobRunrTaskScheduler(jobScheduler, storageProvider);
  }

  @AfterEach
  void tearDown() {
    JobRunr.destroy();
  }

  @Test
  void enqueuesTaskHandlerInvocation() {
    taskScheduler.enqueue("task-id");

    await().until(() -> storageProvider.countJobs(StateName.SUCCEEDED) == 1);
    ArgumentCaptor<JobContext> context = ArgumentCaptor.forClass(JobContext.class);
    verify(taskHandler).executeTask(org.mockito.ArgumentMatchers.eq("task-id"), context.capture());
    assertThat(context.getValue()).isNotSameAs(JobContext.Null);
    assertThat(context.getValue().currentRetry()).isZero();
  }

  @Test
  void schedulesTaskHandlerInvocation() {
    LocalDateTime executionTime = LocalDateTime.now().plusMinutes(5);

    taskScheduler.schedule(executionTime, "task-id");

    await().until(() -> storageProvider.countJobs(StateName.SCHEDULED) == 1);
  }

  @Test
  void registersAndDeletesRecurringInvocation() {
    taskScheduler.scheduleRecurring("check-mail", "0 */15 * * *", "recurring-task-id");

    await().untilAsserted(() -> assertThat(storageProvider.getRecurringJobs()).hasSize(1));

    taskScheduler.deleteRecurring("check-mail");

    await().untilAsserted(() -> assertThat(storageProvider.getRecurringJobs()).isEmpty());
  }

  private JobActivator jobActivator() {
    return new JobActivator() {
      @Override
      public <T> T activateJob(Class<T> type) throws JobActivatorShutdownException {
        if (TaskHandler.class.equals(type)) {
          return type.cast(taskHandler);
        }
        if (RecurringTaskHandler.class.equals(type)) {
          return type.cast(recurringTaskHandler);
        }
        throw new IllegalStateException("Type " + type + " is unknown");
      }
    };
  }
}
