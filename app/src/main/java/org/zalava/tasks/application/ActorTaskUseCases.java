package org.zalava.tasks.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.application.port.out.TaskScheduler;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

/** Uses opaque actor/task tokens at the task-command and scheduler boundary. */
public final class ActorTaskUseCases implements ActorTaskCommands {
  private final ActorTaskStore tasks;
  private final TaskScheduler scheduler;

  public ActorTaskUseCases(ActorTaskStore tasks, TaskScheduler scheduler) {
    this.tasks = tasks;
    this.scheduler = scheduler;
  }

  @Override
  public ActorTaskReference create(Actor actor, String name, String description) {
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, Task.newTask(name, description));
    scheduler.enqueue(new ActorTaskExecutionReference(actor, reference).encode());
    return reference;
  }

  @Override
  public ActorTaskReference schedule(
      Actor actor, LocalDateTime executionTime, String name, String description) {
    ActorTaskReference reference = ActorTaskReference.newReference();
    Instant createdAt = executionTime.atZone(ZoneId.systemDefault()).toInstant();
    tasks.save(actor, reference, Task.newTask(name, createdAt, description));
    scheduler.schedule(executionTime, new ActorTaskExecutionReference(actor, reference).encode());
    return reference;
  }

  @Override
  public Task get(Actor actor, ActorTaskReference reference) {
    return tasks.get(actor, reference);
  }

  @Override
  public List<ActorTaskReference> list(Actor actor) {
    return tasks.list(actor);
  }

  @Override
  public void resume(Actor actor, ActorTaskReference reference) {
    Task task = tasks.get(actor, reference);
    if (task.getStatus() != Task.Status.awaiting_human_input) {
      throw new IllegalStateException("Only tasks awaiting human input can be resumed");
    }
    tasks.save(actor, reference, task.withStatus(Task.Status.todo));
    scheduler.enqueue(new ActorTaskExecutionReference(actor, reference).encode());
  }
}
