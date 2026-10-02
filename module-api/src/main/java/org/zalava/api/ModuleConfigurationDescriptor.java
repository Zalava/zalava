package org.zalava.api;

import java.util.Map;
import java.util.Objects;

/** JSON Schema contract for a module document whose properties are factory ids. */
public record ModuleConfigurationDescriptor(Map<String, Object> jsonSchema) {
  public ModuleConfigurationDescriptor {
    jsonSchema = Map.copyOf(Objects.requireNonNull(jsonSchema, "jsonSchema must not be null"));
  }

  public static ModuleConfigurationDescriptor none() {
    return new ModuleConfigurationDescriptor(Map.of("type", "object", "properties", Map.of()));
  }
}
