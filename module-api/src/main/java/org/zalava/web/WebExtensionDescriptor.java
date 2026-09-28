package org.zalava.web;

public record WebExtensionDescriptor(
    String moduleId, String extensionId, String displayName, String description) {}
