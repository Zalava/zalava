package org.zalava.api.extensions.tasks;

import java.util.List;
import org.zalava.api.InvocationContext;

/** Host-owned task operations available only to the scoped task provider factory. */
public interface TaskService {
  TaskServiceResult create(InvocationContext context, String name, String description);

  TaskServiceResult schedule(
      InvocationContext context, String executionTime, String name, String description);

  TaskServiceResult scheduleRecurring(
      InvocationContext context, String cronExpression, String name, String description);

  TaskServiceResult deleteRecurring(InvocationContext context, String recurringTaskName);

  List<RecurringTaskSummary> listRecurring(InvocationContext context);
}
