package org.zalava.observability.application.port.out;

import java.util.List;

/**
 * Framework-free, fail-open operational observation boundary.
 *
 * <p>Inputs deliberately exclude actor identity, prompts, identifiers, paths, arguments, results
 * and error messages. Implementations must treat observations as optional diagnostics, never as
 * application control flow.
 */
public interface OperationalMetrics {
  OperationalMetrics NOOP = new OperationalMetrics() {};

  default void agentRun(String outcome, long durationMillis, List<ContextUse> contextUse) {}

  default void toolInvocation(String provider, String tool, String outcome, long durationMillis) {}

  default void taskExecution(String outcome, String state, long durationMillis) {}

  default void knowledgeLifecycle(String operation, String outcome) {}

  record ContextUse(String sourceCategory, int charactersUsed) {}
}
