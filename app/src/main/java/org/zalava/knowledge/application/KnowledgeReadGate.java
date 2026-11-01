package org.zalava.knowledge.application;

import java.util.Map;
import java.util.Optional;

/** Gates a Knowledge read before it accesses evidence. */
@FunctionalInterface
public interface KnowledgeReadGate {
  Optional<String> beforeRead(String operation, Map<String, Object> arguments);

  /** Automatic context enrichment cannot present an approval request to the user. */
  default boolean blockAutomaticRead() {
    return false;
  }
}
