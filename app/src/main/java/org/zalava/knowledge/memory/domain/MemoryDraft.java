package org.zalava.knowledge.memory.domain;

import java.util.Map;
import java.util.Objects;

public record MemoryDraft(
    MemoryScope scope, String text, Map<String, String> metadata, MemoryProvenance provenance) {
  public MemoryDraft {
    Objects.requireNonNull(scope, "scope must not be null");
    text = Memory.text(text, "text");
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    Objects.requireNonNull(provenance, "provenance must not be null");
  }

  /** Backward-compatible draft without explicit provenance; persisted as {@code legacy}. */
  public MemoryDraft(MemoryScope scope, String text, Map<String, String> metadata) {
    this(scope, text, metadata, MemoryProvenance.legacy());
  }
}
