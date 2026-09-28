package org.zalava.tasks.application.port.in;

import java.time.LocalDateTime;
import org.zalava.tasks.domain.TaskReference;

public interface TaskCommands {

  TaskReference create(String name, String description);

  TaskReference schedule(LocalDateTime executionTime, String name, String description);

  void scheduleRecurrently(String cronExpression, String name, String description);

  void deleteRecurringTask(String name);

  void createTaskFromRecurringTask(String recurringTaskId);

  void resume(TaskReference reference);
}
