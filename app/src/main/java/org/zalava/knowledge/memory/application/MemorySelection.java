package org.zalava.knowledge.memory.application;

import java.util.List;
import java.util.Objects;
import org.zalava.knowledge.memory.domain.Memory;

/**
 * Deterministic result of selecting memories for context injection, together with the metrics that
 * explain why candidates were kept or omitted.
 *
 * @param selected ordered memories that survived ranking, deduplication and bounds
 * @param metrics candidate/selection counts and injected character count
 */
public record MemorySelection(List<Memory> selected, Metrics metrics) {

  public MemorySelection {
    selected = List.copyOf(Objects.requireNonNull(selected, "selected must not be null"));
    Objects.requireNonNull(metrics, "metrics must not be null");
    if (metrics.selected() != selected.size()) {
      throw new IllegalArgumentException("metrics.selected must match the selected size");
    }
  }

  /**
   * Selection metrics. {@code candidates} is the bounded input size, {@code afterDeduplication} the
   * size once duplicate texts are collapsed, and the {@code omittedBy*} counts partition the
   * remaining candidates by the reason they were dropped.
   */
  public record Metrics(
      int candidates,
      int afterDeduplication,
      int selected,
      int omittedByRelevance,
      int omittedByLimit,
      int omittedByBudget,
      int characters) {
    public Metrics {
      if (candidates < 0
          || afterDeduplication < 0
          || selected < 0
          || omittedByRelevance < 0
          || omittedByLimit < 0
          || omittedByBudget < 0
          || characters < 0) {
        throw new IllegalArgumentException("Memory selection metrics must not be negative");
      }
      if (selected > afterDeduplication) {
        throw new IllegalArgumentException("selected must not exceed afterDeduplication");
      }
    }
  }
}
