package org.zalava.assistant.conversation.domain;

import java.util.Objects;

/** Host-owned provenance; transport handles never form conversation ownership. */
public record ConversationOrigin(
    String channelId, String subject, String deliveryHandle, boolean privateDestination) {
  public ConversationOrigin {
    Objects.requireNonNull(channelId);
    Objects.requireNonNull(subject);
    Objects.requireNonNull(deliveryHandle);
    if (channelId.isBlank()
        || (!channelId.equals("web") && (subject.isBlank() || deliveryHandle.isBlank()))) {
      throw new IllegalArgumentException("Conversation origin is incomplete");
    }
  }

  public static ConversationOrigin web() {
    return new ConversationOrigin("web", "", "", true);
  }

  public boolean webChat() {
    return channelId.equals("web");
  }
}
