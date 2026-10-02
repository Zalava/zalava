package org.zalava.assistant.agent.application.port.out;

import java.util.concurrent.atomic.AtomicInteger;

/** Per-invocation structured-output attempt evidence, scoped to the calling thread. */
public final class StructuredRunEvidence {
  private static final ThreadLocal<AtomicInteger> ATTEMPTS = new ThreadLocal<>();

  private StructuredRunEvidence() {}

  /**
   * Starts a structured invocation and returns the attempt counter the model advisor must
   * increment. The counter is shared with advisor threads so attempt evidence survives advisors
   * running on a model executor rather than the caller.
   */
  public static AtomicInteger begin() {
    AtomicInteger attempts = new AtomicInteger();
    ATTEMPTS.set(attempts);
    return attempts;
  }

  public static int consumeOrDefault(int defaultValue) {
    AtomicInteger attempts = ATTEMPTS.get();
    ATTEMPTS.remove();
    return attempts == null ? defaultValue : attempts.get();
  }
}
