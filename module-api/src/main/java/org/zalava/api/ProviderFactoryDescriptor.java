package org.zalava.api;

public record ProviderFactoryDescriptor(
    String factoryId,
    String moduleId,
    String providerType,
    String displayName,
    String description) {}
