package org.zalava.operation.application.port.in;

import java.util.Objects;
import org.zalava.InvocationContext;
import org.zalava.SeaOperationResult;
import org.zalava.operation.application.model.ToolApproval;
import org.zalava.tasks.domain.TaskReference;

public interface ProviderToolOperations {

  ToolInvocationOutcome invoke(ToolInvocationCommand command);

  ToolInvocationOutcome allowUnscoped(String requestId);

  ToolInvocationOutcome allowUnscopedTool(String requestId);

  ToolApproval denyUnscoped(String requestId);

  enum ApprovalMode {
    OPERATOR,
    TASK
  }

  enum ToolInvocationStatus {
    EXECUTED,
    PENDING_APPROVAL,
    DENIED
  }

  record ToolInvocationCommand(
      String providerId,
      String toolName,
      String argumentsJson,
      InvocationContext context,
      ApprovalMode approvalMode,
      TaskReference taskReference) {
    public ToolInvocationCommand {
      Objects.requireNonNull(providerId, "providerId");
      Objects.requireNonNull(toolName, "toolName");
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(approvalMode, "approvalMode");
      argumentsJson = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
    }

    public static ToolInvocationCommand operator(
        String providerId, String toolName, String argumentsJson, InvocationContext context) {
      return new ToolInvocationCommand(
          providerId, toolName, argumentsJson, context, ApprovalMode.OPERATOR, null);
    }

    public static ToolInvocationCommand task(
        String providerId,
        String toolName,
        String argumentsJson,
        InvocationContext context,
        TaskReference taskReference) {
      return new ToolInvocationCommand(
          providerId, toolName, argumentsJson, context, ApprovalMode.TASK, taskReference);
    }
  }

  record ToolInvocationOutcome(
      ToolInvocationStatus status, SeaOperationResult result, ToolApproval approval) {
    public static ToolInvocationOutcome executed(SeaOperationResult result) {
      return new ToolInvocationOutcome(ToolInvocationStatus.EXECUTED, result, null);
    }

    public static ToolInvocationOutcome pending(ToolApproval approval) {
      return new ToolInvocationOutcome(ToolInvocationStatus.PENDING_APPROVAL, null, approval);
    }

    public static ToolInvocationOutcome denied(ToolApproval approval) {
      return new ToolInvocationOutcome(ToolInvocationStatus.DENIED, null, approval);
    }
  }
}
