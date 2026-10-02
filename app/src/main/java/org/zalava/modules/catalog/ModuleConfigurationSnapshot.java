package org.zalava.modules.catalog;

import java.util.Map;

/** Redactable host-owned configuration snapshot; values are never secret values. */
public record ModuleConfigurationSnapshot(
    String moduleId,
    String version,
    String schemaId,
    Map<String, Object> factories,
    Map<String, String> secretReferences) {
  public ModuleConfigurationSnapshot {
    factories = Map.copyOf(factories);
    secretReferences = Map.copyOf(secretReferences);
  }
}
