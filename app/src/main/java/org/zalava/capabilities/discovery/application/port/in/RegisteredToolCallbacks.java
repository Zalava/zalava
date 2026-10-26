package org.zalava.capabilities.discovery.application.port.in;

import java.util.List;

/** Host integration selects a callback representation; discovery clients need no vendor API. */
public interface RegisteredToolCallbacks<T> {
  List<T> callbacks();

  T callback(String providerId, String toolName);

  /** Stable callback identity even when an adapter materializes a new wrapper. */
  default boolean sameCallback(Object first, Object second) {
    return first.equals(second);
  }
}
