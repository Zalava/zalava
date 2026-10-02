package org.zalava.capabilities.operation.application.model;

import java.util.Map;
import org.zalava.tasks.domain.TaskReference;

public record ToolApproval(
    String requestId,
    String providerId,
    String toolName,
    String actorId,
    Map<String, String> attributes,
    Map<String, String> providerScope,
    String argumentsJson,
    TaskReference taskReference,
    Decision decision,
    ApprovalScope approvalScope) {
  public ToolApproval {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    providerScope = providerScope == null ? Map.of() : Map.copyOf(providerScope);
    approvalScope = approvalScope == null ? ApprovalScope.ONCE : approvalScope;
  }

  public enum Decision {
    PENDING,
    ALLOWED,
    DENIED
  }

  public enum ApprovalScope {
    ONCE,
    TOOL
  }
}
