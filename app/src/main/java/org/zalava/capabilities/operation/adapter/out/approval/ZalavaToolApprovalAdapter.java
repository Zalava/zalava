package org.zalava.capabilities.operation.adapter.out.approval;

import java.util.Optional;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.model.ToolApproval;
import org.zalava.capabilities.operation.application.port.out.ToolApprovalPort;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class ZalavaToolApprovalAdapter implements ToolApprovalPort {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ZalavaToolApprovalRequests requests;

  public ZalavaToolApprovalAdapter(ZalavaToolApprovalRequests requests) {
    this.requests = requests;
  }

  @Override
  public ToolApproval create(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      String argumentsJson,
      TaskReference taskReference) {
    return map(requests.create(provider, tool, context, arguments(argumentsJson), taskReference));
  }

  @Override
  public Optional<ToolApproval> consumeDecision(
      TaskReference taskReference, String providerId, String toolName, String argumentsJson) {
    return requests
        .consumeDecision(taskReference, providerId, toolName, arguments(argumentsJson))
        .map(ZalavaToolApprovalAdapter::map);
  }

  @Override
  public Optional<ToolApproval> findUnscopedDecision(
      String actorId, String providerId, String toolName, String argumentsJson) {
    return requests
        .findUnscopedDecision(actorId, providerId, toolName, arguments(argumentsJson))
        .map(ZalavaToolApprovalAdapter::map);
  }

  @Override
  public Optional<ToolApproval> findAllowedToolPolicy(
      String actorId,
      String providerId,
      String toolName,
      java.util.Map<String, String> providerScope) {
    return requests
        .findAllowedToolPolicy(actorId, providerId, toolName, providerScope)
        .map(ZalavaToolApprovalAdapter::map);
  }

  @Override
  public Optional<ToolApproval> findAllowedToolPolicy(
      InvocationContext context, ZalavaProvider provider, ZalavaToolDescriptor tool) {
    return requests
        .findAllowedToolPolicy(context, provider, tool)
        .map(ZalavaToolApprovalAdapter::map);
  }

  @Override
  public ToolApproval allowUnscoped(String requestId) {
    try {
      return map(requests.allowUnscoped(requestId));
    } catch (ZalavaToolApprovalRequests.NotFoundException ex) {
      throw new ApprovalNotFoundException(requestId);
    } catch (ZalavaToolApprovalRequests.AlreadyDecidedException
        | ZalavaToolApprovalRequests.WrongScopeException ex) {
      throw new ApprovalConflictException(ex.getMessage());
    }
  }

  @Override
  public ToolApproval allowUnscopedTool(String requestId) {
    try {
      return map(requests.allowUnscopedTool(requestId));
    } catch (ZalavaToolApprovalRequests.NotFoundException ex) {
      throw new ApprovalNotFoundException(requestId);
    } catch (ZalavaToolApprovalRequests.AlreadyDecidedException
        | ZalavaToolApprovalRequests.WrongScopeException ex) {
      throw new ApprovalConflictException(ex.getMessage());
    }
  }

  @Override
  public ToolApproval denyUnscoped(String requestId) {
    try {
      return map(requests.denyUnscoped(requestId));
    } catch (ZalavaToolApprovalRequests.NotFoundException ex) {
      throw new ApprovalNotFoundException(requestId);
    } catch (ZalavaToolApprovalRequests.AlreadyDecidedException
        | ZalavaToolApprovalRequests.WrongScopeException ex) {
      throw new ApprovalConflictException(ex.getMessage());
    }
  }

  private static ToolApproval map(ZalavaToolApprovalRequests.Entry entry) {
    return new ToolApproval(
        entry.requestId(),
        entry.providerId(),
        entry.toolName(),
        entry.actorId(),
        entry.attributes(),
        entry.scope(),
        entry.argumentsJson(),
        taskReference(entry.taskReference()),
        ToolApproval.Decision.valueOf(entry.decision().name()),
        ToolApproval.ApprovalScope.valueOf(entry.approvalScope().name()));
  }

  private static TaskReference taskReference(String path) {
    if (path == null) {
      return null;
    }
    String[] parts = path.split("/", 2);
    return TaskReference.parse(parts[0], parts[1]);
  }

  private static tools.jackson.databind.JsonNode arguments(String argumentsJson) {
    try {
      return JSON.readTree(argumentsJson);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("Unable to restore Zalava tool approval arguments", ex);
    }
  }
}
