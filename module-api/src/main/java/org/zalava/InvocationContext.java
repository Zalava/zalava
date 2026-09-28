package org.zalava;

import java.util.Map;

public record InvocationContext(String actorId, boolean confirmed, Map<String, String> attributes) {

  public InvocationContext {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }

  public static InvocationContext system() {
    return new InvocationContext("system", false, Map.of());
  }
}
