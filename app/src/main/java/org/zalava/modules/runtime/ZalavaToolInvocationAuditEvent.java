package org.zalava.modules.runtime;

import java.util.List;
import java.util.Map;

public record ZalavaToolInvocationAuditEvent(
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
    String resultPreview,
    long durationMillis) {
  public ZalavaToolInvocationAuditEvent {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
  }
}
