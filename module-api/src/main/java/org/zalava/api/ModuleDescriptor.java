package org.zalava.api;

public record ModuleDescriptor(
    String moduleId, String version, String displayName, String description) {}
