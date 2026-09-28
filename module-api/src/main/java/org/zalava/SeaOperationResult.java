package org.zalava;

import java.util.Map;

public record SeaOperationResult(boolean success, Object content, Map<String, Object> metadata) {

  public SeaOperationResult {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static SeaOperationResult success(Object content) {
    return new SeaOperationResult(true, content, Map.of());
  }

  public static SeaOperationResult failure(Object content) {
    return new SeaOperationResult(false, content, Map.of());
  }
}
