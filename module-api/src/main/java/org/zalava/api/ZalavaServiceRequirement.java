package org.zalava.api;

/** A module's explicit dependency on one SEA-owned service contract. */
public record ZalavaServiceRequirement(
    String serviceId, String versionRange, RequirementMode mode) {

  public ZalavaServiceRequirement {
    if (serviceId == null || serviceId.isBlank())
      throw new IllegalArgumentException("serviceId must not be blank");
    if (versionRange == null || versionRange.isBlank())
      throw new IllegalArgumentException("versionRange must not be blank");
    if (mode == null) throw new IllegalArgumentException("mode must not be null");
  }
}
