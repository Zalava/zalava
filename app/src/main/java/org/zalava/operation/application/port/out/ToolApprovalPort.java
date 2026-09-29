package org.zalava.operation.application.port.out;

import java.util.Map;
import java.util.Optional;
import org.zalava.InvocationContext;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.operation.application.model.ToolApproval;
import org.zalava.tasks.domain.TaskReference;

public interface ToolApprovalPort {

  ToolApproval create(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      String argumentsJson,
      TaskReference taskReference);

  Optional<ToolApproval> consumeDecision(
      TaskReference taskReference, String providerId, String toolName, String argumentsJson);

  Optional<ToolApproval> findUnscopedDecision(
      String actorId, String providerId, String toolName, String argumentsJson);

  Optional<ToolApproval> findAllowedToolPolicy(
      String actorId, String providerId, String toolName, Map<String, String> providerScope);

  /** Resolves a durable policy using the complete SEA invocation authority. */
  default Optional<ToolApproval> findAllowedToolPolicy(
      InvocationContext context, ZalavaProvider provider, ZalavaToolDescriptor tool) {
    return findAllowedToolPolicy(
        context.actorId(),
        provider.descriptor().providerId(),
        tool.name(),
        provider.descriptor().scope());
  }

  ToolApproval allowUnscoped(String requestId);

  ToolApproval allowUnscopedTool(String requestId);

  ToolApproval denyUnscoped(String requestId);

  final class ApprovalNotFoundException extends RuntimeException {
    public ApprovalNotFoundException(String requestId) {
      super(requestId);
    }
  }

  final class ApprovalConflictException extends RuntimeException {
    public ApprovalConflictException(String requestId) {
      super(requestId);
    }
  }
}
