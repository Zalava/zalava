package org.zalava.memory.domain;

import java.util.Set;

/**
 * Explicit durable-memory identity. A scope names the kind of knowledge a record carries, which
 * determines whether it survives across turns and whether it is eligible for context injection.
 *
 * <p>{@link #USER}, {@link #PROJECT} and {@link #AGENT} are durable knowledge about the acting
 * actor and are eligible for selective context injection. {@link #EXECUTION} is the bounded trace
 * of a single run and is deliberately excluded from durable context so temporary execution details
 * are never promoted implicitly.
 */
public enum MemoryScope {
  USER(true),
  PROJECT(true),
  AGENT(true),
  EXECUTION(false);

  private final boolean durable;

  MemoryScope(boolean durable) {
    this.durable = durable;
  }

  /** Whether records in this scope are durable knowledge rather than a temporary run trace. */
  public boolean durable() {
    return durable;
  }

  /** Whether records in this scope may be injected into an agent context by default. */
  public boolean contextEligible() {
    return durable;
  }

  /** The durable scopes eligible for selective context injection. */
  public static Set<MemoryScope> durableScopes() {
    return Set.of(USER, PROJECT, AGENT);
  }

  /** All scopes, for callers that intentionally need unfiltered access. */
  public static Set<MemoryScope> all() {
    return Set.of(values());
  }

  /**
   * Strict parse for persisted or caller-supplied scope values.
   *
   * @throws IllegalArgumentException when the value is blank or is not a defined scope
   */
  public static MemoryScope parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Invalid memory scope: value must not be blank");
    }
    try {
      return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid memory scope: " + value, exception);
    }
  }
}
