package org.zalava.assistant.conversation.domain;

import java.util.List;
import java.util.Objects;

public record ConversationMessage(
    Role role, String text, List<String> attachmentNames, String runId) {
  public enum Role {
    USER,
    ASSISTANT,
    SYSTEM
  }

  public ConversationMessage(Role role, String text, List<String> attachmentNames) {
    this(role, text, attachmentNames, null);
  }

  public ConversationMessage(Role role, String text) {
    this(role, text, List.of(), null);
  }

  public ConversationMessage {
    attachmentNames = attachmentNames == null ? List.of() : List.copyOf(attachmentNames);
    if (attachmentNames.size() > 5
        || attachmentNames.stream()
            .anyMatch(name -> name == null || name.isBlank() || name.length() > 200))
      throw new IllegalArgumentException("Invalid conversation attachment names");
    Objects.requireNonNull(role, "role must not be null");
    if (text == null || text.isBlank())
      throw new IllegalArgumentException("text must not be blank");
  }
}
