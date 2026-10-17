package org.zalava.identity.channels.application;

import java.util.Optional;
import java.util.function.Supplier;

/** Trusted channel identity propagated to provider permission checks during a synchronous call. */
public final class InvocationChannelContext {
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private InvocationChannelContext() {}

  public static <T> T call(String channel, Supplier<T> operation) {
    String previous = CURRENT.get();
    CURRENT.set(channel);
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
}
