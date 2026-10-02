package org.zalava.knowledge.memory.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record Memory(
    String id,
    MemoryScope scope,
    String text,
    Map<String, String> metadata,
    Instant createdAt,
    MemoryProvenance provenance,
    Instant updatedAt) {
  public Memory {
    id = text(id, "id");
    Objects.requireNonNull(scope, "scope must not be null");
    text = text(text, "text");
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(provenance, "provenance must not be null");
    if (updatedAt != null && updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not be before createdAt");
    }
  }

  /** Backward-compatible view for records that were never revised. */
  public Memory(
      String id,
      MemoryScope scope,
      String text,
      Map<String, String> metadata,
      Instant createdAt,
      MemoryProvenance provenance) {
    this(id, scope, text, metadata, createdAt, provenance, null);
  }

  /** Backward-compatible view for records that carry no explicit provenance. */
  public Memory(
      String id, MemoryScope scope, String text, Map<String, String> metadata, Instant createdAt) {
    this(id, scope, text, metadata, createdAt, MemoryProvenance.legacy(), null);
  }

  /** Returns a copy with revised scope/text, preserving identity, provenance and metadata. */
  public Memory revised(MemoryScope scope, String text, Instant at) {
    return new Memory(id, scope, text, metadata, createdAt, provenance, at);
  }

  static String text(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value.strip();
  }
}
