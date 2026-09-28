package org.zalava;

import java.util.Set;
import java.util.regex.Pattern;
import org.zalava.managed.ManagedServiceDesiredState;

/**
 * One SEA-managed OCI service a module declares. SEA aggregates declarations, derives the
 * administrator-approved resource grant, and installs only what the administrator approves. The
 * declaration is digest-pinned and validated by SEA's existing managed-service contracts.
 */
public record ManagedServiceDeclaration(
    String serviceId, ManagedServiceDesiredState desiredState, Set<String> dependsOn) {

  private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,62}");

  public ManagedServiceDeclaration {
    if (serviceId == null || !ID.matcher(serviceId).matches()) {
      throw new IllegalArgumentException("serviceId must be a canonical identifier");
    }
    if (desiredState == null) {
      throw new IllegalArgumentException("desiredState must not be null");
    }
    if (!desiredState.resourceId().equals(serviceId)) {
      throw new IllegalArgumentException("serviceId must match the desired-state resource id");
    }
    dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
    if (dependsOn.contains(serviceId)) {
      throw new IllegalArgumentException("a managed service must not depend on itself");
    }
  }

  public ManagedServiceDeclaration(String serviceId, ManagedServiceDesiredState desiredState) {
    this(serviceId, desiredState, Set.of());
  }
}
