package org.zalava.tasks.adapter.in.jobrunr;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jobrunr.jobs.context.JobContext;
import org.junit.jupiter.api.Test;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.platform.storage.private_state.BootstrapPrivateStateBridge;
import org.zalava.tasks.application.ActorTaskExecution;
import org.zalava.tasks.application.port.in.TaskExecution;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

class TaskHandlerTest {

  @Test
  void delegatesRetryableAttemptToTaskExecution() {
    TaskExecution taskExecution = mock(TaskExecution.class);
    JobContext jobContext = mock(JobContext.class);
    TaskHandler handler = new TaskHandler(taskExecution);
    when(jobContext.currentRetry()).thenReturn(2);
    when(taskExecution.execute("task-id", TaskExecution.FailureHandling.RETRYABLE))
        .thenReturn(new TaskExecution.ExecutionOutcome("Task", Task.Status.completed));

    handler.executeTask("task-id", jobContext);

    verify(taskExecution).execute("task-id", TaskExecution.FailureHandling.RETRYABLE);
  }

  @Test
  void delegatesFinalAttemptAsTerminal() {
    TaskExecution taskExecution = mock(TaskExecution.class);
    JobContext jobContext = mock(JobContext.class);
    TaskHandler handler = new TaskHandler(taskExecution);
    when(jobContext.currentRetry()).thenReturn(3);
    when(taskExecution.execute("task-id", TaskExecution.FailureHandling.TERMINAL))
        .thenReturn(new TaskExecution.ExecutionOutcome("Task", Task.Status.failed));

    handler.executeTask("task-id", jobContext);

    verify(taskExecution).execute("task-id", TaskExecution.FailureHandling.TERMINAL);
  }

  @Test
  void dispatchesOpaqueActorTokenWithoutCallingLegacyExecution() {
    TaskExecution legacy = mock(TaskExecution.class);
    ActorTaskExecution actorExecution = mock(ActorTaskExecution.class);
    Actor actor = new Actor(AccountId.newId());
    String token =
        new ActorTaskExecutionReference(actor, ActorTaskReference.newReference()).encode();
    TaskHandler handler =
        new TaskHandler(legacy, actorExecution, (BootstrapPrivateStateBridge) null);
    JobContext jobContext = mock(JobContext.class);
    when(jobContext.currentRetry()).thenReturn(0);

    handler.executeTask(token, jobContext);

    verify(actorExecution).execute(token);
  }
}
