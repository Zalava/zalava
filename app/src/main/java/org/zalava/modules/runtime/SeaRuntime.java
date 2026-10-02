package org.zalava.modules.runtime;

import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/** Compatibility facade for callers not yet migrated to {@link RuntimeQueries}. */
public interface SeaRuntime extends RuntimeQueries, AutoCloseable {

  @Override
  default void close() {
    // Implementations with managed provider instances close them explicitly.
  }
}
