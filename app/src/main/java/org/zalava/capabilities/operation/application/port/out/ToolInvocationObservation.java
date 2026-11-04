package org.zalava.capabilities.operation.application.port.out;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.zalava.api.ZalavaOperationResult;

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
    long durationMillis,
    Map<String, Object> arguments) {
  public ToolInvocationObservation(
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
    this(
        providerId,
        toolName,
        actorId,
        attributes,
        confirmed,
        classification,
        policyTags,
        sideEffecting,
        success,
        errorType,
        errorMessage,
        result,
        durationMillis,
        Map.of());
  }

  public ToolInvocationObservation {
    arguments =
        arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
  }
}
