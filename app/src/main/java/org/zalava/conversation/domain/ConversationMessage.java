package org.zalava.conversation.domain;

import java.util.Objects;

public record ConversationMessage(Role role, String text) {
  public enum Role {
    USER,
    ASSISTANT,
    SYSTEM
  }

  public ConversationMessage {
    Objects.requireNonNull(role, "role must not be null");
    if (text == null || text.isBlank())
      throw new IllegalArgumentException("text must not be blank");
  }
}
