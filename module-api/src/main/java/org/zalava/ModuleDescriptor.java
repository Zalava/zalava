package org.zalava;

public record ModuleDescriptor(
    String moduleId, String version, String displayName, String description) {}
