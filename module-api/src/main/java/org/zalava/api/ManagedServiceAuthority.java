package org.zalava.api;

import java.util.Objects;

/** Authority Zalava binds to the module factory scope that requests a managed service. */
public final class ManagedServiceAuthority {

  private final String moduleId;

  ManagedServiceAuthority(String moduleId) {
    if (moduleId == null || moduleId.isBlank()) {
      throw new IllegalArgumentException("moduleId must not be blank");
    }
    this.moduleId = moduleId;
  }

  public String moduleId() {
    return moduleId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ManagedServiceAuthority authority
        && moduleId.equals(authority.moduleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(moduleId);
  }
}
