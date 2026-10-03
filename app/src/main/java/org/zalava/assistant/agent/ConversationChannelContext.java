package org.zalava.assistant.agent;

import java.util.Optional;
import java.util.function.Supplier;

/** Carries the Zalava-owned channel identity while a conversational model invokes tools. */
public final class ConversationChannelContext {
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private ConversationChannelContext() {}

  public static <T> T call(String conversationId, Supplier<T> operation) {
    String previous = CURRENT.get();
    CURRENT.set(channel(conversationId));
    try {
      return operation.get();
    } finally {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }

  public static Optional<String> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  private static String channel(String conversationId) {
    return conversationId != null && conversationId.startsWith("telegram-") ? "telegram" : "web";
  }
}
