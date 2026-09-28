package org.zalava.tasks.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.application.port.out.TaskScheduler;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;

public class DefaultTaskUseCases implements TaskCommands, TaskQueries {

  private static final Logger log = LoggerFactory.getLogger(DefaultTaskUseCases.class);

  private final TaskStore taskStore;
  private final TaskScheduler taskScheduler;

  public DefaultTaskUseCases(TaskStore taskStore, TaskScheduler taskScheduler) {
    this.taskStore = taskStore;
    this.taskScheduler = taskScheduler;
  }

  @Override
  public TaskReference create(String name, String description) {
    Task task = taskStore.save(Task.newTask(name, description));
    TaskReference reference = taskStore.getReference(task);
    taskScheduler.enqueue(task.getId());
    log.info("Task '{}' ({}) has been created.", task.getName(), task.getId());
    return reference;
  }

  @Override
  public TaskReference schedule(LocalDateTime executionTime, String name, String description) {
    Instant createdAt = executionTime.atZone(ZoneId.systemDefault()).toInstant();
    Task task = taskStore.save(Task.newTask(name, createdAt, description));
    TaskReference reference = taskStore.getReference(task);
    taskScheduler.schedule(executionTime, task.getId());
    log.info(
        "Task '{}' ({}) has been scheduled at {}.", task.getName(), task.getId(), executionTime);
    return reference;
  }

  @Override
  public void scheduleRecurrently(String cronExpression, String name, String description) {
    RecurringTask recurringTask = taskStore.save(RecurringTask.newRecurringTask(name, description));
    taskScheduler.scheduleRecurring(recurringTask.getName(), cronExpression, recurringTask.getId());
    log.info(
        "Task '{}' ({}) has been scheduled recurrently with cronExpression {}.",
        name,
        recurringTask.getId(),
        cronExpression);
  }

  @Override
  public void deleteRecurringTask(String name) {
    RecurringTask recurringTask =
        taskStore.getAllRecurringTasks().stream()
            .filter(task -> task.getName().equals(name))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Recurring task with name " + name + " was not found"));
    taskScheduler.deleteRecurring(recurringTask.getName());
    taskStore.deleteRecurringTask(recurringTask.getId());
    log.info("Recurring task '{}' ({}) has been deleted.", name, recurringTask.getId());
  }

  @Override
  public void createTaskFromRecurringTask(String recurringTaskId) {
    RecurringTask recurringTask = taskStore.getRecurringTaskById(recurringTaskId);
    Task task =
        taskStore.save(Task.newTask(recurringTask.getName(), recurringTask.getDescription()));
    taskScheduler.enqueue(task.getId());
    log.info("Task '{}' ({}) has been created from recurring task.", task.getName(), task.getId());
  }

  @Override
  public void resume(TaskReference reference) {
    Task task = taskStore.getTask(reference);
    if (task.getStatus() != Task.Status.awaiting_human_input) {
      throw new IllegalStateException("Only tasks awaiting human input can be resumed");
    }
    Task queued = taskStore.save(task.withStatus(Task.Status.todo));
    taskScheduler.enqueue(queued.getId());
    log.info(
        "Task '{}' ({}) has been requeued after an approval decision.",
        queued.getName(),
        queued.getId());
  }

  @Override
  public Task getTask(TaskReference reference) {
    return taskStore.getTask(reference);
  }

  @Override
  public TaskReference getReference(Task task) {
    return taskStore.getReference(task);
  }

  @Override
  public List<Task> getTasks(LocalDate localDate, Task.Status status) {
    return taskStore.getTasks(localDate, status);
  }

  @Override
  public List<Task> getAllTasks() {
    return taskStore.getAllTasks();
  }

  @Override
  public List<RecurringTask> getAllRecurringTasks() {
    return taskStore.getAllRecurringTasks();
  }
}
