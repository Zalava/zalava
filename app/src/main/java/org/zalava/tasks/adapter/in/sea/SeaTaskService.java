package org.zalava.tasks.adapter.in.sea;

import java.time.LocalDateTime;
import java.util.List;
import org.zalava.InvocationContext;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.RecurringTaskSummary;
import org.zalava.tasks.TaskService;
import org.zalava.tasks.TaskServiceResult;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.capture.ActorTaskCreationContext;

/** Host adapter that keeps task persistence and scheduling inside SEA. */
public final class SeaTaskService implements TaskService {
  private final TaskCommands commands;
  private final TaskQueries queries;
  private final ActorTaskCommands actorCommands;
  private final ActorTaskCreationContext actorTaskCreation;
  private final ActorExecutionContext actorExecution;

  public SeaTaskService(TaskCommands commands, TaskQueries queries) {
    this(commands, queries, null, null, null);
  }

  public SeaTaskService(
      TaskCommands commands,
      TaskQueries queries,
      ActorTaskCommands actorCommands,
      ActorTaskCreationContext actorTaskCreation,
      ActorExecutionContext actorExecution) {
    this.commands = commands;
    this.queries = queries;
    this.actorCommands = actorCommands;
    this.actorTaskCreation = actorTaskCreation;
    this.actorExecution = actorExecution;
  }

  @Override
  public TaskServiceResult create(InvocationContext context, String name, String description) {
    requireActor(context);
    Actor actor = productActor(context);
    if (actor != null) {
      var reference = actorCommands.create(actor, name, description);
      actorTaskCreation.taskCreated(reference);
      return TaskServiceResult.created(new org.zalava.tasks.TaskReference(reference.value()));
    }
    return TaskServiceResult.created(reference(commands.create(name, description)));
  }

  @Override
  public TaskServiceResult schedule(
      InvocationContext context, String executionTime, String name, String description) {
    requireActor(context);
    Actor actor = productActor(context);
    if (actor != null) {
      var reference =
          actorCommands.schedule(actor, LocalDateTime.parse(executionTime), name, description);
      actorTaskCreation.taskCreated(reference);
      return TaskServiceResult.scheduled(new org.zalava.tasks.TaskReference(reference.value()));
    }
    return TaskServiceResult.scheduled(
        reference(commands.schedule(LocalDateTime.parse(executionTime), name, description)));
  }

  @Override
  public TaskServiceResult scheduleRecurring(
      InvocationContext context, String cronExpression, String name, String description) {
    requireActor(context);
    requireLegacyRecurringContext(context);
    commands.scheduleRecurrently(cronExpression, name, description);
    return TaskServiceResult.recurringScheduled(findRecurring(name));
  }

  @Override
  public TaskServiceResult deleteRecurring(InvocationContext context, String recurringTaskName) {
    requireActor(context);
    requireLegacyRecurringContext(context);
    RecurringTaskSummary task = findRecurring(recurringTaskName);
    commands.deleteRecurringTask(recurringTaskName);
    return TaskServiceResult.recurringDeleted(task);
  }

  @Override
  public List<RecurringTaskSummary> listRecurring(InvocationContext context) {
    requireActor(context);
    requireLegacyRecurringContext(context);
    return queries.getAllRecurringTasks().stream()
        .map(task -> new RecurringTaskSummary(task.getId(), task.getName(), task.getDescription()))
        .toList();
  }

  private RecurringTaskSummary findRecurring(String name) {
    return queries.getAllRecurringTasks().stream()
        .filter(task -> task.getName().equals(name))
        .findFirst()
        .map(task -> new RecurringTaskSummary(task.getId(), task.getName(), task.getDescription()))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Recurring task with name " + name + " was not found"));
  }

  private static org.zalava.tasks.TaskReference reference(
      org.zalava.tasks.domain.TaskReference reference) {
    return new org.zalava.tasks.TaskReference(reference.path());
  }

  private static void requireActor(InvocationContext context) {
    if (context == null || context.actorId() == null || context.actorId().isBlank())
      throw new IllegalArgumentException("Task service requires an actor");
  }

  private Actor productActor(InvocationContext context) {
    if (actorExecution == null || actorExecution.currentPrincipal().isEmpty()) return null;
    if (actorCommands == null || actorTaskCreation == null)
      throw new IllegalStateException("Actor task service is not configured");
    return actorExecution.currentPrincipal().orElseThrow().actor();
  }

  private void requireLegacyRecurringContext(InvocationContext context) {
    if (actorExecution != null && actorExecution.currentPrincipal().isPresent()) {
      throw new UnsupportedOperationException(
          "Actor-scoped recurring jobs are not available in this product baseline");
    }
  }
}
