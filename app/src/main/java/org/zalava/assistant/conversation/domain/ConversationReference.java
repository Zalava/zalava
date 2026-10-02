package org.zalava.assistant.conversation.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque public identity for a conversation; it never encodes a filesystem location. */
public record ConversationReference(String value) {
  public ConversationReference {
    Objects.requireNonNull(value, "value");
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid conversation reference", exception);
    }
  }

  public static ConversationReference newReference() {
    return new ConversationReference(UUID.randomUUID().toString());
  }
}
