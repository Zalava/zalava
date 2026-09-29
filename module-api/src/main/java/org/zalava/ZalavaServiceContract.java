package org.zalava;

import java.util.Objects;

/** Stable, SEA-owned identifier for one typed module service contract. */
public record ZalavaServiceContract<T>(
    String serviceId, String contractVersion, Class<T> serviceType) {

  public ZalavaServiceContract {
    requireText(serviceId, "serviceId");
    requireText(contractVersion, "contractVersion");
    serviceType = Objects.requireNonNull(serviceType, "serviceType must not be null");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
