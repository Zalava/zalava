package org.zalava.tasks.adapter.in.jobrunr;

import org.zalava.private_state.BootstrapPrivateStateBridge;
import org.zalava.tasks.application.ActorTaskExecution;
import org.zalava.tasks.application.port.in.TaskExecution;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskHandler {

  private static final int JOB_RETRIES = 3;
  private static final Logger LOGGER =
      new JobRunrDashboardLogger(LoggerFactory.getLogger(TaskHandler.class));

  private final TaskExecution taskExecution;
  private final ActorTaskExecution actorTaskExecution;
  private final BootstrapPrivateStateBridge privateStateBridge;

  public TaskHandler(TaskExecution taskExecution) {
    this(taskExecution, null, (BootstrapPrivateStateBridge) null);
  }

  @Autowired
  public TaskHandler(
      TaskExecution taskExecution,
      ActorTaskExecution actorTaskExecution,
      java.util.Optional<BootstrapPrivateStateBridge> privateStateBridge) {
    this(taskExecution, actorTaskExecution, privateStateBridge.orElse(null));
  }

  /** Compatibility constructor used while legacy JobRunr payloads still exist. */
  public TaskHandler(
      TaskExecution taskExecution,
      ActorTaskExecution actorTaskExecution,
      BootstrapPrivateStateBridge privateStateBridge) {
    this.taskExecution = taskExecution;
    this.actorTaskExecution = actorTaskExecution;
    this.privateStateBridge = privateStateBridge;
  }

  @Job(name = "%0", retries = JOB_RETRIES)
  public void executeTask(String taskId, JobContext jobContext) {
    TaskExecution.FailureHandling failureHandling =
        jobContext.currentRetry() >= JOB_RETRIES
            ? TaskExecution.FailureHandling.TERMINAL
            : TaskExecution.FailureHandling.RETRYABLE;
    LOGGER.info("Starting task: {}", taskId);
    ActorTaskExecutionReference actorExecution = actorExecution(taskId);
    if (actorExecution != null) {
      if (actorTaskExecution == null) {
        throw new IllegalStateException("Actor-scoped task execution is not configured");
      }
      LOGGER.info(
          "Finished actor task: {} with status {}",
          taskId,
          actorTaskExecution.execute(actorExecution.encode()));
      return;
    }
    TaskExecution.ExecutionOutcome outcome = taskExecution.execute(taskId, failureHandling);
    LOGGER.info("Finished task: {} with status {}", outcome.taskName(), outcome.status());
  }

  private ActorTaskExecutionReference actorExecution(String taskId) {
    try {
      return ActorTaskExecutionReference.parse(taskId);
    } catch (IllegalArgumentException ignored) {
      return privateStateBridge == null
          ? null
          : privateStateBridge.resolveLegacyScheduledTask(taskId);
    }
  }
}
