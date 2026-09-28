package org.zalava.tasks.application.port.out;

import java.time.LocalDate;
import java.util.List;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;

public interface TaskStore {

  Task save(Task task);

  Task getTaskById(String id);

  Task getTask(TaskReference reference);

  TaskReference getReference(Task task);

  List<Task> getTasks(LocalDate localDate, Task.Status status);

  List<Task> getAllTasks();

  RecurringTask save(RecurringTask recurringTask);

  RecurringTask getRecurringTaskById(String id);

  List<RecurringTask> getAllRecurringTasks();

  void deleteRecurringTask(String id);
}
