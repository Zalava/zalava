package org.zalava.operation.application.port.out;

import java.util.List;
import java.util.Map;
import org.zalava.ZalavaOperationResult;

public record ToolInvocationObservation(
    String providerId,
    String toolName,
    String actorId,
    Map<String, String> attributes,
    boolean confirmed,
    String classification,
    List<String> policyTags,
    boolean sideEffecting,
    boolean success,
    String errorType,
    String errorMessage,
    ZalavaOperationResult result,
    long durationMillis) {
  public ToolInvocationObservation {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
  }
}
