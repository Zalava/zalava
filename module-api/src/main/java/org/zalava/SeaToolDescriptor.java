package org.zalava;

import java.util.List;
import java.util.Map;

public record SeaToolDescriptor(
    String name,
    String description,
    boolean sideEffecting,
    List<String> policyTags,
    Map<String, Object> inputSchema) {
  public SeaToolDescriptor(String name, String description, boolean sideEffecting) {
    this(name, description, sideEffecting, List.of(), Map.of());
  }

  public SeaToolDescriptor(
      String name, String description, boolean sideEffecting, List<String> policyTags) {
    this(name, description, sideEffecting, policyTags, Map.of());
  }

  public SeaToolDescriptor {
    policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
    inputSchema = SeaToolInputSchemas.immutable(inputSchema);
  }
}
