package org.zalava.knowledge.application;

import java.util.List;
import java.util.Map;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObservation;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObserver;

public final class KnowledgeToolObservation {
  private final List<ToolInvocationObserver> observers;

  public KnowledgeToolObservation(List<ToolInvocationObserver> observers) {
    this.observers = List.copyOf(observers);
  }

  public void record(String operation, String actorId, String outcome, long durationMillis) {
    var observation =
        new ToolInvocationObservation(
            "knowledge",
            operation,
            actorId,
            Map.of("outcome", outcome),
            false,
            "ACTOR_AUTHORIZED_READ",
            List.of("knowledge", "private-by-default"),
            false,
            List.of("OK", "NO_MATCH", "NOT_FOUND").contains(outcome),
            null,
            null,
            null,
            durationMillis);
    for (var observer : observers) {
      try {
        observer.observe(observation);
      } catch (RuntimeException ignored) {
        /* Optional diagnostics cannot alter evidence. */
      }
    }
  }
}
