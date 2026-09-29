package org.zalava.operation.adapter.out.approval;

import java.util.Optional;
import org.zalava.InvocationContext;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.operation.application.model.ToolApproval;
import org.zalava.operation.application.port.out.ToolApprovalPort;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class SeaToolApprovalAdapter implements ToolApprovalPort {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final SeaToolApprovalRequests requests;

  public SeaToolApprovalAdapter(SeaToolApprovalRequests requests) {
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
        .map(SeaToolApprovalAdapter::map);
  }

  @Override
  public Optional<ToolApproval> findUnscopedDecision(
      String actorId, String providerId, String toolName, String argumentsJson) {
    return requests
        .findUnscopedDecision(actorId, providerId, toolName, arguments(argumentsJson))
        .map(SeaToolApprovalAdapter::map);
  }

  @Override
  public Optional<ToolApproval> findAllowedToolPolicy(
      String actorId,
      String providerId,
      String toolName,
      java.util.Map<String, String> providerScope) {
    return requests
        .findAllowedToolPolicy(actorId, providerId, toolName, providerScope)
        .map(SeaToolApprovalAdapter::map);
  }

  @Override
  public Optional<ToolApproval> findAllowedToolPolicy(
      InvocationContext context, ZalavaProvider provider, ZalavaToolDescriptor tool) {
    return requests.findAllowedToolPolicy(context, provider, tool).map(SeaToolApprovalAdapter::map);
  }

  @Override
  public ToolApproval allowUnscoped(String requestId) {
    try {
      return map(requests.allowUnscoped(requestId));
    } catch (SeaToolApprovalRequests.NotFoundException ex) {
      throw new ApprovalNotFoundException(requestId);
    } catch (SeaToolApprovalRequests.AlreadyDecidedException
        | SeaToolApprovalRequests.WrongScopeException ex) {
      throw new ApprovalConflictException(ex.getMessage());
    }
  }

  @Override
  public ToolApproval allowUnscopedTool(String requestId) {
    try {
      return map(requests.allowUnscopedTool(requestId));
    } catch (SeaToolApprovalRequests.NotFoundException ex) {
      throw new ApprovalNotFoundException(requestId);
    } catch (SeaToolApprovalRequests.AlreadyDecidedException
        | SeaToolApprovalRequests.WrongScopeException ex) {
      throw new ApprovalConflictException(ex.getMessage());
    }
  }

  @Override
  public ToolApproval denyUnscoped(String requestId) {
    try {
      return map(requests.denyUnscoped(requestId));
    } catch (SeaToolApprovalRequests.NotFoundException ex) {
      throw new ApprovalNotFoundException(requestId);
    } catch (SeaToolApprovalRequests.AlreadyDecidedException
        | SeaToolApprovalRequests.WrongScopeException ex) {
      throw new ApprovalConflictException(ex.getMessage());
    }
  }

  private static ToolApproval map(SeaToolApprovalRequests.Entry entry) {
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
      throw new IllegalArgumentException("Unable to restore SEA tool approval arguments", ex);
    }
  }
}
