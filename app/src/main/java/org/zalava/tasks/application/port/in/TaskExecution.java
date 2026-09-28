package org.zalava.tasks.application.port.in;

import org.zalava.tasks.domain.Task;

public interface TaskExecution {

  ExecutionOutcome execute(String taskId, FailureHandling failureHandling);

  enum FailureHandling {
    RETRYABLE,
    TERMINAL
  }

  record ExecutionOutcome(String taskName, Task.Status status) {}
}
