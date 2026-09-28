package org.zalava;

public record ProviderFactoryDescriptor(
    String factoryId,
    String moduleId,
    String providerType,
    String displayName,
    String description) {}
