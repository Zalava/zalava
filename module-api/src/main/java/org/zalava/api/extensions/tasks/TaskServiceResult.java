package org.zalava.api.extensions.tasks;

/** Immutable result for a host-owned task operation. */
public record TaskServiceResult(
    String status, TaskReference taskReference, RecurringTaskSummary recurringTask) {
  public static TaskServiceResult created(TaskReference reference) {
    return new TaskServiceResult("created", reference, null);
  }

  public static TaskServiceResult scheduled(TaskReference reference) {
    return new TaskServiceResult("scheduled", reference, null);
  }

  public static TaskServiceResult recurringScheduled(RecurringTaskSummary task) {
    return new TaskServiceResult("recurring_scheduled", null, task);
  }

  public static TaskServiceResult recurringDeleted(RecurringTaskSummary task) {
    return new TaskServiceResult("recurring_deleted", null, task);
  }
}
