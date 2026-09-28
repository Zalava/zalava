package org.zalava.knowledge.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque identifier for one independently retained user-provided source. */
public record KnowledgeSourceId(UUID value) {
  public KnowledgeSourceId {
    Objects.requireNonNull(value, "value");
  }

  public static KnowledgeSourceId create() {
    return new KnowledgeSourceId(UUID.randomUUID());
  }
}
