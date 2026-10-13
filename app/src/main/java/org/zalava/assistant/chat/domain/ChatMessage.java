package org.zalava.assistant.chat.domain;

import java.util.List;

public record ChatMessage(Role role, String text, List<String> attachmentNames) {
  public ChatMessage(Role role, String text) {
    this(role, text, List.of());
  }

  public ChatMessage {
    attachmentNames = attachmentNames == null ? List.of() : List.copyOf(attachmentNames);
  }

  public enum Role {
    USER,
    ASSISTANT,
    SYSTEM
  }
}
