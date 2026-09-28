package org.zalava.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.zalava.tasks.domain.ActorTaskReference;

/** Captures opaque actor-owned jobs created during a product chat turn. */
@Component
public final class ActorTaskCreationContext {
  private final ThreadLocal<List<ActorTaskReference>> currentReferences = new ThreadLocal<>();

  public <T> Capture<T> capture(Supplier<T> operation) {
    List<ActorTaskReference> previous = currentReferences.get();
    List<ActorTaskReference> references = new ArrayList<>();
    currentReferences.set(references);
    try {
      return new Capture<>(operation.get(), references);
    } finally {
      if (previous == null) currentReferences.remove();
      else currentReferences.set(previous);
    }
  }

  public void taskCreated(ActorTaskReference reference) {
    List<ActorTaskReference> references = currentReferences.get();
    if (references != null) references.add(reference);
  }

  /** Current capture list on this thread, or {@code null} when no capture is active. */
  public List<ActorTaskReference> currentReferences() {
    return currentReferences.get();
  }

  /**
   * Replaces the capture list on this thread, sharing the given instance so references recorded on
   * this thread (e.g. from a streamed tool invocation) are visible to the capture owner. The
   * previous list is returned for restoration; a {@code null} input clears the ThreadLocal.
   */
  public List<ActorTaskReference> installReferences(List<ActorTaskReference> references) {
    List<ActorTaskReference> previous = currentReferences.get();
    if (references == null) currentReferences.remove();
    else currentReferences.set(references);
    return previous;
  }

  public record Capture<T>(T value, List<ActorTaskReference> taskReferences) {
    public Capture {
      taskReferences = List.copyOf(taskReferences);
    }
  }
}
