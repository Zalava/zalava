package org.zalava.api;

import java.util.Map;

public record ZalavaOperationResult(boolean success, Object content, Map<String, Object> metadata) {

  public ZalavaOperationResult {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static ZalavaOperationResult success(Object content) {
    return new ZalavaOperationResult(true, content, Map.of());
  }

  public static ZalavaOperationResult failure(Object content) {
    return new ZalavaOperationResult(false, content, Map.of());
  }
}
