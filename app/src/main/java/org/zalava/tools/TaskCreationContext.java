package org.zalava.tools;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.zalava.tasks.domain.TaskReference;
import org.springframework.stereotype.Component;

@Component
public class TaskCreationContext implements TaskEventHandler {

  private final ThreadLocal<List<TaskReference>> currentReferences = new ThreadLocal<>();

  public <T> Capture<T> capture(Supplier<T> operation) {
    List<TaskReference> previous = currentReferences.get();
    List<TaskReference> references = new ArrayList<>();
    currentReferences.set(references);
    try {
      return new Capture<>(operation.get(), references);
    } finally {
      if (previous == null) {
        currentReferences.remove();
      } else {
        currentReferences.set(previous);
      }
    }
  }

  @Override
  public void taskCreated(TaskReference reference, String name, String description) {
    record(reference);
  }

  @Override
  public void taskScheduled(
      TaskReference reference, LocalDateTime executionTime, String name, String description) {
    record(reference);
  }

  @Override
  public void recurringTaskCreated(String cronExpression, String name, String description) {
    // A recurring template is not a concrete job instance.
  }

  private void record(TaskReference reference) {
    List<TaskReference> references = currentReferences.get();
    if (references != null) {
      references.add(reference);
    }
  }

  public record Capture<T>(T value, List<TaskReference> taskReferences) {

    public Capture {
      taskReferences = List.copyOf(taskReferences);
    }
  }
}
