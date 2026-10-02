package org.zalava.api.extensions.managed;

import java.time.Duration;
import java.util.Set;

/** Administrator-approved maximum resources for one module-owned managed service. */
public record ManagedServiceResourceGrant(
    String moduleId,
    Set<String> secretReferences,
    Set<String> dataPaths,
    Set<Integer> ports,
    Set<String> devices,
    ManagedServiceLimits limits,
    Duration maximumReadinessDeadline,
    int maximumRestartLimit) {

  public ManagedServiceResourceGrant {
    ManagedServiceDesiredState.requireId(moduleId, "moduleId");
    secretReferences = secretReferences == null ? Set.of() : Set.copyOf(secretReferences);
    secretReferences.forEach(
        value -> ManagedServiceDesiredState.requireId(value, "secretReferences"));
    dataPaths = ManagedServicePaths.immutablePaths(dataPaths, "dataPaths", false);
    ports = ports == null ? Set.of() : Set.copyOf(ports);
    if (ports.stream().anyMatch(port -> port == null || port < 1 || port > 65535)) {
      throw new IllegalArgumentException("ports must be between 1 and 65535");
    }
    devices = ManagedServicePaths.immutablePaths(devices, "devices", true);
    if (limits == null || maximumReadinessDeadline == null) {
      throw new IllegalArgumentException("managed-service grant must be complete");
    }
    if (maximumReadinessDeadline.isNegative() || maximumReadinessDeadline.isZero()) {
      throw new IllegalArgumentException("maximumReadinessDeadline must be positive");
    }
    if (maximumRestartLimit < 0) {
      throw new IllegalArgumentException("maximumRestartLimit must not be negative");
    }
  }
}
