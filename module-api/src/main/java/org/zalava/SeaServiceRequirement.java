package org.zalava;

/** A module's explicit dependency on one SEA-owned service contract. */
public record SeaServiceRequirement(String serviceId, String versionRange, RequirementMode mode) {

  public SeaServiceRequirement {
    if (serviceId == null || serviceId.isBlank())
      throw new IllegalArgumentException("serviceId must not be blank");
    if (versionRange == null || versionRange.isBlank())
      throw new IllegalArgumentException("versionRange must not be blank");
    if (mode == null) throw new IllegalArgumentException("mode must not be null");
  }
}
