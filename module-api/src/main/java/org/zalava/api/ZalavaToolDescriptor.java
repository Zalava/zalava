package org.zalava.api;

import java.util.List;
import java.util.Map;

public record ZalavaToolDescriptor(
    String name,
    String description,
    boolean sideEffecting,
    List<String> policyTags,
    Map<String, Object> inputSchema) {
  public ZalavaToolDescriptor(String name, String description, boolean sideEffecting) {
    this(name, description, sideEffecting, List.of(), Map.of());
  }

  public ZalavaToolDescriptor(
      String name, String description, boolean sideEffecting, List<String> policyTags) {
    this(name, description, sideEffecting, policyTags, Map.of());
  }

  public ZalavaToolDescriptor {
    policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
    inputSchema = ZalavaToolInputSchemas.immutable(inputSchema);
  }
}
