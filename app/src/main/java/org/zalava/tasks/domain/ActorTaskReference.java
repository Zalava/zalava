package org.zalava.tasks.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque public task reference, valid only when paired with an authenticated Zalava actor. */
public record ActorTaskReference(String value) {
  public ActorTaskReference {
    Objects.requireNonNull(value, "value");
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid actor task reference", exception);
    }
  }

  public static ActorTaskReference newReference() {
    return new ActorTaskReference(UUID.randomUUID().toString());
  }
}
