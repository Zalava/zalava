package org.zalava.tasks.application.port.out;

import java.time.LocalDateTime;

public interface TaskScheduler {

  void enqueue(String taskId);

  void schedule(LocalDateTime executionTime, String taskId);

  void scheduleRecurring(String recurringTaskName, String cronExpression, String recurringTaskId);

  void deleteRecurring(String recurringTaskName);
}
