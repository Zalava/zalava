package org.zalava.capabilities.operation.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.model.ToolApproval;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.operation.application.port.out.ToolApprovalPort;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObservation;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObserver;

public final class DefaultProviderToolOperations implements ProviderToolOperations {

  private final org.zalava.capabilities.operation.application.port.out.ToolArgumentDecoder decoder;
  private final ProviderCatalog providerCatalog;
  private final ToolApprovalPort approvalPort;
  private final List<ToolInvocationObserver> observers;
  private final MemberProviderCapabilityPolicy memberCapabilities;

  public DefaultProviderToolOperations(
      ProviderCatalog providerCatalog,
      ToolApprovalPort approvalPort,
      List<ToolInvocationObserver> observers,
      org.zalava.capabilities.operation.application.port.out.ToolArgumentDecoder decoder) {
    this(providerCatalog, approvalPort, observers, new MemberProviderCapabilityPolicy(), decoder);
  }

  public DefaultProviderToolOperations(
      ProviderCatalog providerCatalog,
      ToolApprovalPort approvalPort,
      List<ToolInvocationObserver> observers,
      MemberProviderCapabilityPolicy memberCapabilities,
      org.zalava.capabilities.operation.application.port.out.ToolArgumentDecoder decoder) {
    this.decoder = java.util.Objects.requireNonNull(decoder);
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
    Map<String, Object> arguments = decoder.decode(command.argumentsJson());

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
    Map<String, Object> arguments = decoder.decode(approval.argumentsJson());
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
                    "Zalava provider not found: " + providerId));
  }

  private static ZalavaToolDescriptor findTool(ZalavaProvider provider, String toolName) {
    return provider.listTools().stream()
        .filter(tool -> tool.name().equals(toolName))
        .findFirst()
        .orElseThrow(
            () ->
                new ProviderToolOperationException(
                    ProviderToolOperationException.Code.TOOL_NOT_FOUND,
                    "Zalava provider tool not found: " + toolName));
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
        "Zalava permission denied invocation",
        null,
        System.nanoTime());
    return ToolInvocationOutcome.denied(approval);
  }

  private ZalavaOperationResult execute(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      Map<String, Object> arguments,
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
          ProviderToolOperationException.Code.EXECUTION, "Zalava operation failed", ex);
    }
  }

  private static ZalavaOperationResult alreadyExecuted(ToolApproval approval) {
    return ZalavaOperationResult.success(
        Map.of(
            "status", "already_executed",
            "approvalRequestId", approval.requestId(),
            "message",
                "This permission request was already approved and executed from Zalava control."));
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
    approved.put("source", "zalava-control-permission-request");
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
    return "zalava_backed";
  }

  private static ToolApproval approvalDecision(java.util.function.Supplier<ToolApproval> decision) {
    try {
      return decision.get();
    } catch (ToolApprovalPort.ApprovalNotFoundException ex) {
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.APPROVAL_NOT_FOUND,
          "Zalava tool approval request not found: " + ex.getMessage(),
          ex);
    } catch (ToolApprovalPort.ApprovalConflictException ex) {
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.APPROVAL_CONFLICT, ex.getMessage(), ex);
    }
  }
}
