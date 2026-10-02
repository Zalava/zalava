package org.zalava.tasks.capture;

import java.time.LocalDateTime;
import org.zalava.tasks.domain.TaskReference;

/** Captures task references without exposing a direct chat callback. */
public interface TaskEventHandler {

  void taskCreated(TaskReference reference, String name, String description);

  void taskScheduled(
      TaskReference reference, LocalDateTime executionTime, String name, String description);

  void recurringTaskCreated(String cronExpression, String name, String description);
}
