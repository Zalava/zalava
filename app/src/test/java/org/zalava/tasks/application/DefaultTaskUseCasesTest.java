package org.zalava.tasks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.tasks.application.port.out.TaskScheduler;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;

class DefaultTaskUseCasesTest {

  private final TaskStore taskStore = mock(TaskStore.class);
  private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
  private final DefaultTaskUseCases useCases = new DefaultTaskUseCases(taskStore, taskScheduler);

  @Test
  void createsTaskBeforeEnqueuingIt() {
    Task saved = task("task-id", Task.Status.todo);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-handle-email.md");
    when(taskStore.save(any(Task.class))).thenReturn(saved);
    when(taskStore.getReference(saved)).thenReturn(reference);

    TaskReference result = useCases.create("handle-email", "Process unread email messages");

    assertThat(result).isEqualTo(reference);
    var order = inOrder(taskStore, taskScheduler);
    order.verify(taskStore).save(any(Task.class));
    order.verify(taskStore).getReference(saved);
    order.verify(taskScheduler).enqueue("task-id");
  }

  @Test
  void schedulesTaskWithExecutionTimeAsCreatedAt() {
    LocalDateTime executionTime = LocalDateTime.of(2026, 6, 9, 13, 30);
    TaskReference reference = TaskReference.parse("2026-06-09", "133000-weekly-summary.md");
    when(taskStore.save(any(Task.class)))
        .thenAnswer(
            invocation -> {
              Task task = invocation.getArgument(0);
              return new Task(
                  "scheduled-task-id",
                  task.getName(),
                  task.getCreatedAt(),
                  task.getStatus(),
                  task.getDescription());
            });
    when(taskStore.getReference(any(Task.class))).thenReturn(reference);

    TaskReference result =
        useCases.schedule(executionTime, "weekly-summary", "Prepare the weekly summary");

    assertThat(result).isEqualTo(reference);
    var task = org.mockito.ArgumentCaptor.forClass(Task.class);
    verify(taskStore).save(task.capture());
    assertThat(task.getValue().getCreatedAt())
        .isEqualTo(executionTime.atZone(ZoneId.systemDefault()).toInstant());
    verify(taskScheduler).schedule(executionTime, "scheduled-task-id");
  }

  @Test
  void registersRecurringTaskAfterPersistingItsTemplate() {
    RecurringTask saved = new RecurringTask("recurring-id", "check-mail", "Check mail");
    when(taskStore.save(any(RecurringTask.class))).thenReturn(saved);

    useCases.scheduleRecurrently("0 */15 * * *", "check-mail", "Check mail");

    var order = inOrder(taskStore, taskScheduler);
    order.verify(taskStore).save(any(RecurringTask.class));
    order.verify(taskScheduler).scheduleRecurring("check-mail", "0 */15 * * *", "recurring-id");
  }

  @Test
  void createsNormalTaskFromRecurringTemplate() {
    RecurringTask recurringTask = new RecurringTask("recurring-id", "check-mail", "Check mail");
    Task saved = task("spawned-task-id", Task.Status.todo);
    when(taskStore.getRecurringTaskById("recurring-id")).thenReturn(recurringTask);
    when(taskStore.save(any(Task.class))).thenReturn(saved);

    useCases.createTaskFromRecurringTask("recurring-id");

    var order = inOrder(taskStore, taskScheduler);
    order.verify(taskStore).getRecurringTaskById("recurring-id");
    order.verify(taskStore).save(any(Task.class));
    order.verify(taskScheduler).enqueue("spawned-task-id");
  }

  @Test
  void deletesRecurringTaskFromSchedulerAndStore() {
    RecurringTask recurringTask = new RecurringTask("recurring-id", "check-mail", "Check mail");
    when(taskStore.getAllRecurringTasks()).thenReturn(List.of(recurringTask));

    useCases.deleteRecurringTask("check-mail");

    var order = inOrder(taskScheduler, taskStore);
    order.verify(taskScheduler).deleteRecurring("check-mail");
    order.verify(taskStore).deleteRecurringTask("recurring-id");
  }

  @Test
  void resumesOnlyTasksAwaitingHumanInput() {
    Task waiting = task("task-id", Task.Status.awaiting_human_input);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-handle-email.md");
    when(taskStore.getTask(reference)).thenReturn(waiting);
    when(taskStore.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

    useCases.resume(reference);

    var queued = org.mockito.ArgumentCaptor.forClass(Task.class);
    verify(taskStore).save(queued.capture());
    assertThat(queued.getValue().getStatus()).isEqualTo(Task.Status.todo);
    verify(taskScheduler).enqueue("task-id");
  }

  @Test
  void rejectsResumeForTaskThatIsNotWaiting() {
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-handle-email.md");
    when(taskStore.getTask(reference)).thenReturn(task("task-id", Task.Status.in_progress));

    assertThatThrownBy(() -> useCases.resume(reference))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only tasks awaiting human input can be resumed");
  }

  @Test
  void exposesTaskQueriesWithoutLeakingTheStoreToCallers() {
    LocalDate date = LocalDate.of(2026, 6, 8);
    Task task = task("task-id", Task.Status.completed);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-handle-email.md");
    when(taskStore.getTasks(date, Task.Status.completed)).thenReturn(List.of(task));
    when(taskStore.getAllTasks()).thenReturn(List.of(task));
    when(taskStore.getTask(reference)).thenReturn(task);
    when(taskStore.getReference(task)).thenReturn(reference);

    assertThat(useCases.getTasks(date, Task.Status.completed)).containsExactly(task);
    assertThat(useCases.getAllTasks()).containsExactly(task);
    assertThat(useCases.getTask(reference)).isEqualTo(task);
    assertThat(useCases.getReference(task)).isEqualTo(reference);
  }

  private static Task task(String id, Task.Status status) {
    return new Task(
        id,
        "handle-email",
        Instant.parse("2026-06-08T10:00:00Z"),
        status,
        "Process unread email messages");
  }
}
