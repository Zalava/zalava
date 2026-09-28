package org.zalava.chat.domain;

public record ChatMessage(Role role, String text) {
  public enum Role {
    USER,
    ASSISTANT,
    SYSTEM
  }
}
