package org.zalava.api.extensions.web;

public record WebExtensionDescriptor(
    String moduleId, String extensionId, String displayName, String description) {}
