package org.zalava.tasks.domain;

import java.util.Optional;
import java.util.function.Supplier;

public class TaskExecutionContext {

  private final ThreadLocal<TaskReference> currentTask = new ThreadLocal<>();
  private final ThreadLocal<ActorTaskExecutionReference> currentActorTask = new ThreadLocal<>();

  public <T> T call(TaskReference taskReference, Supplier<T> operation) {
    TaskReference previous = currentTask.get();
    currentTask.set(taskReference);
    try {
      return operation.get();
    } finally {
      if (previous == null) {
        currentTask.remove();
      } else {
        currentTask.set(previous);
      }
    }
  }

  public Optional<TaskReference> currentTaskReference() {
    return Optional.ofNullable(currentTask.get());
  }

  public <T> T call(ActorTaskExecutionReference taskReference, Supplier<T> operation) {
    ActorTaskExecutionReference previous = currentActorTask.get();
    currentActorTask.set(taskReference);
    try {
      return operation.get();
    } finally {
      if (previous == null) currentActorTask.remove();
      else currentActorTask.set(previous);
    }
  }

  public Optional<ActorTaskExecutionReference> currentActorTaskReference() {
    return Optional.ofNullable(currentActorTask.get());
  }
}
