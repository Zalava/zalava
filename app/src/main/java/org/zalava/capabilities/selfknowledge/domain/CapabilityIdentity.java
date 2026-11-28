package org.zalava.capabilities.selfknowledge.domain;

import java.util.Optional;

/** Stable identity for a module capability; invalid external identifiers are not exposed. */
public record CapabilityIdentity(String value) {
  public CapabilityIdentity {
    if (value == null || !value.startsWith("module:") || !validPart(value.substring(7))) {
      throw new IllegalArgumentException("Invalid module capability identity");
    }
  }

  public static Optional<CapabilityIdentity> module(String moduleId) {
    return validPart(moduleId)
        ? Optional.of(new CapabilityIdentity("module:" + moduleId))
        : Optional.empty();
  }

  public static boolean validPart(String value) {
    return value != null && value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,159}");
  }
}
