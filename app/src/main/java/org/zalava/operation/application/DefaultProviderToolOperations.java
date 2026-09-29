package org.zalava.operation.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.zalava.InvocationContext;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.operation.application.model.ToolApproval;
import org.zalava.operation.application.port.in.ProviderToolOperationException;
import org.zalava.operation.application.port.in.ProviderToolOperations;
import org.zalava.operation.application.port.out.ProviderCatalog;
import org.zalava.operation.application.port.out.ToolApprovalPort;
import org.zalava.operation.application.port.out.ToolInvocationObservation;
import org.zalava.operation.application.port.out.ToolInvocationObserver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class DefaultProviderToolOperations implements ProviderToolOperations {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ProviderCatalog providerCatalog;
  private final ToolApprovalPort approvalPort;
  private final List<ToolInvocationObserver> observers;
  private final MemberProviderCapabilityPolicy memberCapabilities;

  public DefaultProviderToolOperations(
      ProviderCatalog providerCatalog,
      ToolApprovalPort approvalPort,
      List<ToolInvocationObserver> observers) {
    this(providerCatalog, approvalPort, observers, new MemberProviderCapabilityPolicy());
  }

  public DefaultProviderToolOperations(
      ProviderCatalog providerCatalog,
      ToolApprovalPort approvalPort,
      List<ToolInvocationObserver> observers,
      MemberProviderCapabilityPolicy memberCapabilities) {
    this.providerCatalog = providerCatalog;
    this.approvalPort = approvalPort;
    this.observers = List.copyOf(observers);
    this.memberCapabilities = memberCapabilities;
  }

  @Override
  public ToolInvocationOutcome invoke(ToolInvocationCommand command) {
    ZalavaProvider provider = findProvider(command.providerId());
    ZalavaToolDescriptor tool = findTool(provider, command.toolName());
    memberCapabilities.requireAllowed(provider, tool, command.context());
    JsonNode arguments = parseArguments(command.argumentsJson());

    if (!tool.sideEffecting()) {
      return ToolInvocationOutcome.executed(execute(provider, tool, arguments, command.context()));
    }

    if (command.taskReference() != null) {
      Optional<ToolApproval> decision =
          approvalPort.consumeDecision(
              command.taskReference(),
              command.providerId(),
              command.toolName(),
              command.argumentsJson());
      if (decision.isPresent()) {
        ToolApproval approval = decision.get();
        if (approval.decision() == ToolApproval.Decision.DENIED)
          return denied(provider, tool, command.context(), approval);
        return ToolInvocationOutcome.executed(
            execute(
                provider,
                tool,
                arguments,
                confirmedContext(command.context(), approval.requestId())));
      }
    }

    Optional<ToolApproval> policy =
        approvalPort.findAllowedToolPolicy(command.context(), provider, tool);
    if (policy.isPresent()) {
      return ToolInvocationOutcome.executed(
          execute(
              provider,
              tool,
              arguments,
              approvedByToolPolicyContext(command.context(), policy.get().requestId())));
    }

    if (command.approvalMode() == ApprovalMode.OPERATOR) {
      return ToolInvocationOutcome.pending(
          approvalPort.create(provider, tool, command.context(), command.argumentsJson(), null));
    }

    if (command.approvalMode() == ApprovalMode.TASK) {
      Optional<ToolApproval> decision =
          approvalPort.findUnscopedDecision(
              command.context().actorId(),
              command.providerId(),
              command.toolName(),
              command.argumentsJson());
      if (decision.isPresent()) {
        ToolApproval approval = decision.get();
        if (approval.decision() == ToolApproval.Decision.DENIED)
          return denied(provider, tool, command.context(), approval);
        return ToolInvocationOutcome.executed(alreadyExecuted(approval));
      }
    }

    return ToolInvocationOutcome.pending(
        approvalPort.create(
            provider, tool, command.context(), command.argumentsJson(), command.taskReference()));
  }

  @Override
  public ToolInvocationOutcome allowUnscoped(String requestId) {
    return executeUnscopedApproval(approvalDecision(() -> approvalPort.allowUnscoped(requestId)));
  }

  @Override
  public ToolInvocationOutcome allowUnscopedTool(String requestId) {
    return executeUnscopedApproval(
        approvalDecision(() -> approvalPort.allowUnscopedTool(requestId)));
  }

  private ToolInvocationOutcome executeUnscopedApproval(ToolApproval approval) {
    ZalavaProvider provider = findProvider(approval.providerId());
    ZalavaToolDescriptor tool = findTool(provider, approval.toolName());
    JsonNode arguments = parseArguments(approval.argumentsJson());
    InvocationContext context =
        new InvocationContext(
            approval.actorId(),
            true,
            operatorApprovedAttributes(approval.attributes(), approval.requestId()));
    return ToolInvocationOutcome.executed(execute(provider, tool, arguments, context));
  }

  @Override
  public ToolApproval denyUnscoped(String requestId) {
    return approvalDecision(() -> approvalPort.denyUnscoped(requestId));
  }

  private ZalavaProvider findProvider(String providerId) {
    return providerCatalog
        .findProvider(providerId)
        .orElseThrow(
            () ->
                new ProviderToolOperationException(
                    ProviderToolOperationException.Code.PROVIDER_NOT_FOUND,
                    "SEA provider not found: " + providerId));
  }

  private static ZalavaToolDescriptor findTool(ZalavaProvider provider, String toolName) {
    return provider.listTools().stream()
        .filter(tool -> tool.name().equals(toolName))
        .findFirst()
        .orElseThrow(
            () ->
                new ProviderToolOperationException(
                    ProviderToolOperationException.Code.TOOL_NOT_FOUND,
                    "SEA provider tool not found: " + toolName));
  }

  private ToolInvocationOutcome denied(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      ToolApproval approval) {
    Map<String, String> attributes = new LinkedHashMap<>(context.attributes());
    attributes.put("approvalDecision", "denied");
    attributes.put("approvalRequestId", approval.requestId());
    observe(
        provider,
        tool,
        new InvocationContext(context.actorId(), false, Map.copyOf(attributes)),
        false,
        "permission_denied",
        "SEA permission denied invocation",
        null,
        System.nanoTime());
    return ToolInvocationOutcome.denied(approval);
  }

  private ZalavaOperationResult execute(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      JsonNode arguments,
      InvocationContext context) {
    memberCapabilities.requireAllowed(provider, tool, context);
    long startedAt = System.nanoTime();
    try {
      ZalavaOperationResult result = provider.callTool(tool.name(), arguments, context);
      observe(provider, tool, context, true, null, null, result, startedAt);
      return result;
    } catch (UnsupportedOperationException ex) {
      observe(provider, tool, context, false, "unsupported", ex.getMessage(), null, startedAt);
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.UNSUPPORTED, ex.getMessage(), ex);
    } catch (IllegalArgumentException ex) {
      observe(provider, tool, context, false, "validation", ex.getMessage(), null, startedAt);
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.VALIDATION, ex.getMessage(), ex);
    } catch (RuntimeException ex) {
      observe(provider, tool, context, false, "execution", ex.getMessage(), null, startedAt);
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.EXECUTION, "SEA operation failed", ex);
    }
  }

  private static ZalavaOperationResult alreadyExecuted(ToolApproval approval) {
    return ZalavaOperationResult.success(
        Map.of(
            "status", "already_executed",
            "approvalRequestId", approval.requestId(),
            "message",
                "This permission request was already approved and executed from SEA control."));
  }

  private void observe(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      boolean success,
      String errorType,
      String errorMessage,
      ZalavaOperationResult result,
      long startedAt) {
    ToolInvocationObservation observation =
        new ToolInvocationObservation(
            provider.descriptor().providerId(),
            tool.name(),
            context.actorId(),
            context.attributes(),
            context.confirmed(),
            classification(tool),
            tool.policyTags(),
            tool.sideEffecting(),
            success,
            errorType,
            errorMessage,
            result,
            (System.nanoTime() - startedAt) / 1_000_000);
    observers.forEach(observer -> observer.observe(observation));
  }

  private static JsonNode parseArguments(String argumentsJson) {
    try {
      JsonNode arguments = JSON.readTree(argumentsJson);
      if (!arguments.isObject()) {
        throw new ProviderToolOperationException(
            ProviderToolOperationException.Code.VALIDATION,
            "SEA provider tool arguments must be a JSON object");
      }
      return arguments;
    } catch (ProviderToolOperationException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.VALIDATION,
          "SEA provider tool arguments must be valid JSON",
          ex);
    }
  }

  private static InvocationContext confirmedContext(InvocationContext context, String requestId) {
    return new InvocationContext(
        context.actorId(), true, taskApprovedAttributes(context.attributes(), requestId));
  }

  private static InvocationContext approvedByToolPolicyContext(
      InvocationContext context, String requestId) {
    Map<String, String> approved = new LinkedHashMap<>(context.attributes());
    approved.put("approvalPolicy", "tool");
    approved.put("approvalRequestId", requestId);
    return new InvocationContext(context.actorId(), true, Map.copyOf(approved));
  }

  private static Map<String, String> operatorApprovedAttributes(
      Map<String, String> attributes, String requestId) {
    Map<String, String> approved = new LinkedHashMap<>(attributes);
    approved.put("source", "sea-control-permission-request");
    approved.put("permissionRequestId", requestId);
    return Map.copyOf(approved);
  }

  private static Map<String, String> taskApprovedAttributes(
      Map<String, String> attributes, String requestId) {
    Map<String, String> approved = new LinkedHashMap<>(attributes);
    approved.put("approvalRequestId", requestId);
    return Map.copyOf(approved);
  }

  private static String classification(ZalavaToolDescriptor tool) {
    return "sea_backed";
  }

  private static ToolApproval approvalDecision(java.util.function.Supplier<ToolApproval> decision) {
    try {
      return decision.get();
    } catch (ToolApprovalPort.ApprovalNotFoundException ex) {
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.APPROVAL_NOT_FOUND,
          "SEA tool approval request not found: " + ex.getMessage(),
          ex);
    } catch (ToolApprovalPort.ApprovalConflictException ex) {
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.APPROVAL_CONFLICT, ex.getMessage(), ex);
    }
  }
}
