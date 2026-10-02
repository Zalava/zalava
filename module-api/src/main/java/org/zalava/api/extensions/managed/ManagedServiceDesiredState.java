package org.zalava.api.extensions.managed;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable module-declared desired state, independent of any process or OCI engine. */
public record ManagedServiceDesiredState(
    String resourceId,
    String artifactReference,
    String revision,
    ManagedServiceLifecycle lifecycle,
    Set<String> secretReferences,
    Set<String> dataPaths,
    Set<Integer> ports,
    Set<String> devices,
    ManagedServiceLimits limits,
    Duration readinessDeadline,
    int restartLimit) {

  private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,62}");
  private static final Pattern DIGEST = Pattern.compile(".+@sha256:[a-f0-9]{64}");

  public ManagedServiceDesiredState {
    requireId(resourceId, "resourceId");
    if (artifactReference == null || !DIGEST.matcher(artifactReference).matches()) {
      throw new IllegalArgumentException("artifactReference must be SHA-256 digest-pinned");
    }
    if (revision == null || revision.isBlank()) {
      throw new IllegalArgumentException("revision must not be blank");
    }
    if (lifecycle == null || limits == null || readinessDeadline == null) {
      throw new IllegalArgumentException("managed-service desired state must be complete");
    }
    if (readinessDeadline.isNegative() || readinessDeadline.isZero()) {
      throw new IllegalArgumentException("readinessDeadline must be positive");
    }
    if (restartLimit < 0) {
      throw new IllegalArgumentException("restartLimit must not be negative");
    }
    secretReferences = immutableIds(secretReferences, "secretReferences");
    dataPaths = ManagedServicePaths.immutablePaths(dataPaths, "dataPaths", false);
    ports = immutablePorts(ports);
    devices = ManagedServicePaths.immutablePaths(devices, "devices", true);
  }

  static void requireId(String value, String name) {
    if (value == null || !ID.matcher(value).matches()) {
      throw new IllegalArgumentException(name + " must be a canonical identifier");
    }
  }

  private static Set<String> immutableIds(Set<String> values, String name) {
    Set<String> copied = values == null ? Set.of() : Set.copyOf(values);
    copied.forEach(value -> requireId(value, name));
    return copied;
  }

  private static Set<Integer> immutablePorts(Set<Integer> values) {
    Set<Integer> copied = values == null ? Set.of() : Set.copyOf(values);
    if (copied.stream().anyMatch(port -> port == null || port < 1 || port > 65535)) {
      throw new IllegalArgumentException("ports must be between 1 and 65535");
    }
    return copied;
  }
}
