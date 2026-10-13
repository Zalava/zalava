package org.zalava.assistant.conversation.domain;

import java.util.List;

/** Owner-visible display metadata; references and names never establish authorization. */
public record ConversationSummary(String id, String title, String createdAt, String updatedAt) {
  public static String defaultTitle(List<ConversationMessage> messages) {
    String text =
        messages.stream()
            .filter(message -> message.role() == ConversationMessage.Role.USER)
            .map(ConversationMessage::text)
            .findFirst()
            .orElse("")
            .lines()
            .findFirst()
            .orElse("")
            .strip();
    if (text.isBlank()) return "New conversation";
    text = text.replaceAll("\\s+", " ");
    int length = text.codePointCount(0, text.length());
    return length <= 80 ? text : text.substring(0, text.offsetByCodePoints(0, 80)) + "…";
  }

  public static String requireTitle(String title) {
    if (title == null
        || title.isBlank()
        || title.length() > 120
        || title.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException(
          "Conversation name must contain 1 to 120 characters on one line");
    return title.strip();
  }
}
