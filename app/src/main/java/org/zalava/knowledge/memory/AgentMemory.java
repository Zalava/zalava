package org.zalava.knowledge.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AgentMemory(
    String id,
    AgentMemoryScope scope,
    String text,
    Map<String, String> metadata,
    Instant createdAt) {

  public AgentMemory {
    id = requireText(id, "id");
    Objects.requireNonNull(scope, "scope must not be null");
    text = requireText(text, "text");
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value.strip();
  }
}
