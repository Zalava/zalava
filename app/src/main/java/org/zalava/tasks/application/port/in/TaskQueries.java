package org.zalava.tasks.application.port.in;

import java.time.LocalDate;
import java.util.List;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;

public interface TaskQueries {

  Task getTask(TaskReference reference);

  TaskReference getReference(Task task);

  List<Task> getTasks(LocalDate localDate, Task.Status status);

  List<Task> getAllTasks();

  List<RecurringTask> getAllRecurringTasks();
}
