package org.zalava.managed.application.port.in;

import java.util.List;
import java.util.Set;

/**
 * Bounded, read-only projection of SEA-owned managed services for UI and diagnostics consumers.
 * Provider-agnostic: it exposes only SEA's own durable state and declared endpoints, never engine
 * handles or module internals.
 */
public interface ManagedServiceQueries {

  /** Services owned by the given module, ordered by service id; never null. */
  List<ManagedServiceSummary> forModule(String moduleId);

  record ManagedServiceSummary(
      String serviceId,
      String observedState,
      String desiredRevision,
      String observedRevision,
      String artifactReference,
      Set<Integer> ports,
      int consecutiveFailures) {}
}
