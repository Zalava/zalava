package org.zalava.assistant.agent.application.port.out;

import java.util.Optional;

/** A policy result replaces a model-facing host callback result when execution must wait. */
@FunctionalInterface
public interface HostCallbackPolicy {
  Optional<String> beforeCall(String name, String input);

  /** Whether automatic content or identifying metadata must be withheld before confirmation. */
  default boolean blockAutomaticContent(String name) {
    return false;
  }
}
