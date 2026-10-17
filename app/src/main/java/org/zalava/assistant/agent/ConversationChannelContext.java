package org.zalava.assistant.agent;

import java.util.Optional;
import java.util.function.Supplier;
import org.zalava.identity.channels.application.InvocationChannelContext;

/** Carries the Zalava-owned channel identity while a conversational model invokes tools. */
public final class ConversationChannelContext {
  private ConversationChannelContext() {}

  public static <T> T call(String conversationId, Supplier<T> operation) {
    return InvocationChannelContext.call(channel(conversationId), operation);
  }

  public static Optional<String> current() {
    return InvocationChannelContext.current();
  }

  private static String channel(String conversationId) {
    return conversationId != null && conversationId.startsWith("telegram-") ? "telegram" : "web";
  }
}
