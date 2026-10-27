package org.zalava.assistant.agent;

import java.util.Optional;
import java.util.function.Supplier;

/** Host-issued execution identity; never taken from model text or client metadata. */
public final class AgentRunContext {
  public static final String KEY = "zalava.runId";
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private AgentRunContext() {}

  public static Optional<String> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static <T> T call(String id, Supplier<T> operation) {
    String previous = CURRENT.get();
    if (id == null) CURRENT.remove();
    else CURRENT.set(id);
    try {
      return operation.get();
    } finally {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }
}
