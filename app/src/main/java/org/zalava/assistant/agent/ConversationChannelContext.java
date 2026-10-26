package org.zalava.assistant.agent;

import java.util.Optional;
import java.util.function.Supplier;
import org.zalava.identity.channels.application.InvocationChannelContext;

/** Carries the Zalava-owned channel identity while a conversational model invokes tools. */
public final class ConversationChannelContext {
  private static final ThreadLocal<String> CONVERSATION = new ThreadLocal<>();

  private ConversationChannelContext() {}

  public static <T> T call(String conversationId, Supplier<T> operation) {
    String previous = CONVERSATION.get();
    CONVERSATION.set(conversationId);
    try {
      return InvocationChannelContext.call(channel(conversationId), operation);
    } finally {
      if (previous == null) CONVERSATION.remove();
      else CONVERSATION.set(previous);
    }
  }

  public static Optional<String> current() {
    return InvocationChannelContext.current();
  }

  public static Optional<String> conversation() {
    return Optional.ofNullable(CONVERSATION.get());
  }

  private static String channel(String conversationId) {
    return conversationId != null && conversationId.startsWith("telegram-") ? "telegram" : "web";
  }
}
