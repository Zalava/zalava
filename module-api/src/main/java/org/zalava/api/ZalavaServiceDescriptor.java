package org.zalava.api;

/** Immutable declaration for one service implementation owned by a module. */
public record ZalavaServiceDescriptor(String serviceId, String moduleId, String contractVersion) {

  public ZalavaServiceDescriptor {
    requireText(serviceId, "serviceId");
    requireText(moduleId, "moduleId");
    requireText(contractVersion, "contractVersion");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
