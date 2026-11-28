package org.zalava.capabilities.operation.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.model.ToolApproval;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.operation.application.port.out.ToolApprovalPort;
import org.zalava.capabilities.operation.application.port.out.ToolArgumentDecoder;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObservation;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObserver;
import org.zalava.capabilities.operation.domain.ApprovalBinding;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;

public final class DefaultProviderToolOperations implements ProviderToolOperations {

  private final ToolArgumentDecoder decoder;
  private final ProviderCatalog providerCatalog;
  private final ToolApprovalPort approvalPort;
  private final List<ToolInvocationObserver> observers;
  private final MemberProviderCapabilityPolicy memberCapabilities;
  private final ChatToolConfirmation confirmation;
  private final HomeAssistantActionPolicy homeAssistantPolicy;

  public DefaultProviderToolOperations(
      ProviderCatalog providerCatalog,
      ToolApprovalPort approvalPort,
      List<ToolInvocationObserver> observers,
      ToolArgumentDecoder decoder) {
    this(providerCatalog, approvalPort, observers, new MemberProviderCapabilityPolicy(), decoder);
  }

  public DefaultProviderToolOperations(
      ProviderCatalog providerCatalog,
      ToolApprovalPort approvalPort,
      List<ToolInvocationObserver> observers,
      MemberProviderCapabilityPolicy memberCapabilities,
      ToolArgumentDecoder decoder) {
    this(providerCatalog, approvalPort, observers, memberCapabilities, decoder, null);
  }

  public DefaultProviderToolOperations(
      ProviderCatalog providerCatalog,
      ToolApprovalPort approvalPort,
      List<ToolInvocationObserver> observers,
      MemberProviderCapabilityPolicy memberCapabilities,
      ToolArgumentDecoder decoder,
      ChatToolConfirmation confirmation) {
    this(providerCatalog, approvalPort, observers, memberCapabilities, decoder, confirmation, null);
  }

  public DefaultProviderToolOperations(
      ProviderCatalog providerCatalog,
      ToolApprovalPort approvalPort,
      List<ToolInvocationObserver> observers,
      MemberProviderCapabilityPolicy memberCapabilities,
      ToolArgumentDecoder decoder,
      ChatToolConfirmation confirmation,
      AccountLifecycle accounts) {
    this.confirmation = confirmation;
    this.homeAssistantPolicy = accounts == null ? null : new HomeAssistantActionPolicy(accounts);
    this.decoder = Objects.requireNonNull(decoder);
    this.providerCatalog = providerCatalog;
    this.approvalPort = approvalPort;
    this.observers = List.copyOf(observers);
    this.memberCapabilities = memberCapabilities;
  }

  @Override
  public ToolInvocationOutcome invoke(ToolInvocationCommand command) {
    ZalavaProvider provider = findProvider(command.providerId());
    ZalavaToolDescriptor tool = findTool(provider, command.toolName());
    requireMemberAccess(provider, tool, command.context());
    Map<String, Object> arguments = decoder.decode(command.argumentsJson());
    boolean homeAction = HomeAssistantActionPolicy.applies(provider, tool);
    if (homeAction && command.argumentsJson().length() > 1000)
      throw deniedHome(
          provider,
          tool,
          command.context(),
          arguments,
          "Home Assistant action is too large to review");
    if (homeAction) requireHomeAction(provider, tool, arguments, command.context());

    Optional<Boolean> setting =
        confirmation == null || command.taskReference() != null
            ? Optional.empty()
            : confirmation.requireFor(command.context(), provider, tool);
    if (!tool.sideEffecting() && !setting.orElse(false)) {
      return ToolInvocationOutcome.executed(execute(provider, tool, arguments, command.context()));
    }

    if (!tool.sideEffecting()
        && setting.orElse(false)
        && confirmation.mayConsumeReadApproval(command.context())) {
      var approved =
          approvalPort.consumeChatReadDecision(
              command.context(), provider, tool, command.argumentsJson());
      if (approved.isPresent())
        return ToolInvocationOutcome.executed(
            execute(
                provider,
                tool,
                arguments,
                confirmedContext(command.context(), approved.get().requestId())));
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
        if ((homeAction || isHomeApproval(approval))
            && !matchesHomeApproval(approval, provider, command.context(), arguments)) {
          throw deniedHome(
              provider,
              tool,
              command.context(),
              arguments,
              "Home Assistant approval no longer matches the invocation");
        }
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

    if (!homeAction && setting.isPresent() && !setting.get()) {
      Map<String, String> attributes = new LinkedHashMap<>(command.context().attributes());
      attributes.put("approvalPolicy", "chat-confirmation-setting");
      return ToolInvocationOutcome.executed(
          execute(
              provider,
              tool,
              arguments,
              new InvocationContext(command.context().actorId(), true, Map.copyOf(attributes))));
    }
    Optional<ToolApproval> policy =
        setting.orElse(false)
            ? Optional.empty()
            : homeAction
                ? approvalPort.findAllowedToolPolicy(
                    command.context(), provider, tool, command.argumentsJson())
                : approvalPort.findAllowedToolPolicy(command.context(), provider, tool);
    if (homeAction) {
      policy = policy.filter(p -> matchesHomeApproval(p, provider, command.context(), arguments));
    }
    if (policy.isPresent()) {
      return ToolInvocationOutcome.executed(
          execute(
              provider,
              tool,
              arguments,
              approvedByToolPolicyContext(
                  command.context(),
                  policy.get().requestId(),
                  homeAction ? provider.descriptor().moduleId() : null)));
    }

    if (command.approvalMode() == ApprovalMode.OPERATOR) {
      return pending(
          provider,
          tool,
          arguments,
          command,
          approvalPort.create(provider, tool, command.context(), command.argumentsJson(), null));
    }

    if (command.approvalMode() == ApprovalMode.TASK && setting.isEmpty()) {
      boolean directChat =
          "web".equals(command.context().attributes().get("permissionPolicyChannel"))
              && !command.context().attributes().containsKey("actorTaskReference")
              && !command.context().attributes().getOrDefault("conversationId", "").isBlank();
      Optional<ToolApproval> decision =
          homeAction
              ? approvalPort.findScopedDecision(
                  command.context(), provider, tool, command.argumentsJson())
              : directChat
                  ? approvalPort.findDirectChatDecision(
                      command.context(),
                      command.providerId(),
                      command.toolName(),
                      command.argumentsJson())
                  : approvalPort.findUnscopedDecision(
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

    return pending(
        provider,
        tool,
        arguments,
        command,
        approvalPort.create(
            provider, tool, command.context(), command.argumentsJson(), command.taskReference()));
  }

  private ToolInvocationOutcome pending(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      Map<String, Object> arguments,
      ToolInvocationCommand command,
      ToolApproval approval) {
    observe(
        provider,
        tool,
        command.context(),
        false,
        "pending_approval",
        "Awaiting permission",
        ZalavaOperationResult.failure(
            Map.of("status", "pending_approval", "approvalRequestId", approval.requestId())),
        System.nanoTime(),
        arguments);
    return ToolInvocationOutcome.pending(approval);
  }

  @Override
  public ToolApproval approveChatRead(String requestId) {
    return approvalDecision(() -> approvalPort.approveChatRead(requestId));
  }

  @Override
  public ToolInvocationOutcome allowUnscoped(String requestId) {
    validatePendingHomeApproval(requestId);
    return executeUnscopedApproval(approvalDecision(() -> approvalPort.allowUnscoped(requestId)));
  }

  @Override
  public ToolInvocationOutcome allowUnscopedTool(String requestId) {
    validatePendingHomeApproval(requestId);
    return executeUnscopedApproval(
        approvalDecision(() -> approvalPort.allowUnscopedTool(requestId)));
  }

  private void validatePendingHomeApproval(String requestId) {
    ToolApproval approval = approvalDecision(() -> approvalPort.get(requestId));
    ZalavaProvider provider = findProvider(approval.providerId());
    ZalavaToolDescriptor tool = findTool(provider, approval.toolName());
    if (!HomeAssistantActionPolicy.applies(provider, tool) && !isHomeApproval(approval)) return;
    var context = new InvocationContext(approval.actorId(), false, approval.attributes());
    Map<String, Object> arguments = decoder.decode(approval.argumentsJson());
    if (approval.argumentsJson().length() > 1000 && isHomeApproval(approval))
      throw deniedHome(
          provider, tool, context, arguments, "Home Assistant action is too large to review");
    if (!matchesHomeApproval(approval, provider, context, arguments))
      throw deniedHome(
          provider,
          tool,
          context,
          arguments,
          "Home Assistant approval no longer matches the provider");
    requireMemberAccess(provider, tool, context);
    requireHomeAction(provider, tool, arguments, context);
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
    ZalavaOperationResult result;
    try {
      if ((HomeAssistantActionPolicy.applies(provider, tool) || isHomeApproval(approval))
          && !matchesHomeApproval(approval, provider, context, arguments)) {
        throw deniedHome(
            provider,
            tool,
            context,
            arguments,
            "Home Assistant approval no longer matches the provider");
      }
      result = execute(provider, tool, arguments, context);
    } catch (RuntimeException failure) {
      try {
        approvalPort.recordExecutionOutcome(approval.requestId(), false);
      } catch (RuntimeException recordingFailure) {
        failure.addSuppressed(recordingFailure);
      }
      throw failure;
    }
    approvalPort.recordExecutionOutcome(approval.requestId(), result.success());
    return ToolInvocationOutcome.executed(result);
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

  private void requireMemberAccess(
      ZalavaProvider provider, ZalavaToolDescriptor tool, InvocationContext context) {
    try {
      memberCapabilities.requireAllowed(provider, tool, context);
    } catch (ProviderToolOperationException denied) {
      observe(
          provider,
          tool,
          context,
          false,
          "permission_denied",
          denied.getMessage(),
          null,
          System.nanoTime());
      throw denied;
    }
  }

  private ZalavaOperationResult execute(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      Map<String, Object> arguments,
      InvocationContext context) {
    requireMemberAccess(provider, tool, context);
    if (HomeAssistantActionPolicy.applies(provider, tool)) {
      ZalavaProvider current = findProvider(provider.descriptor().providerId());
      if (!current.descriptor().moduleId().equals(provider.descriptor().moduleId())
          || !current.descriptor().scope().equals(provider.descriptor().scope()))
        throw deniedHome(
            provider, tool, context, arguments, "Home Assistant provider changed before dispatch");
      requireHomeAction(provider, tool, arguments, context);
      if ("tool".equals(context.attributes().get("approvalPolicy"))
          && !approvalPort.isScopedPolicyActive(
              context.attributes().get("approvalRequestId"), context, current, tool, arguments))
        throw deniedHome(
            provider, tool, context, arguments, "Home Assistant approval was revoked or changed");
    }
    long startedAt = System.nanoTime();
    try {
      ZalavaOperationResult result = provider.callTool(tool.name(), arguments, context);
      observe(
          provider,
          tool,
          context,
          result.success(),
          result.success() ? null : "provider_failure",
          null,
          result,
          startedAt,
          arguments);
      return result;
    } catch (UnsupportedOperationException ex) {
      observe(
          provider,
          tool,
          context,
          false,
          "unsupported",
          ex.getMessage(),
          null,
          startedAt,
          arguments);
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.UNSUPPORTED, ex.getMessage(), ex);
    } catch (IllegalArgumentException ex) {
      observe(
          provider,
          tool,
          context,
          false,
          "validation",
          ex.getMessage(),
          null,
          startedAt,
          arguments);
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.VALIDATION, ex.getMessage(), ex);
    } catch (RuntimeException ex) {
      observe(
          provider, tool, context, false, "execution", ex.getMessage(), null, startedAt, arguments);
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.EXECUTION, "Zalava operation failed", ex);
    }
  }

  private void requireHomeAction(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      Map<String, Object> arguments,
      InvocationContext context) {
    try {
      if (homeAssistantPolicy == null)
        throw new ProviderToolOperationException(
            ProviderToolOperationException.Code.UNSUPPORTED,
            "Home Assistant action policy unavailable");
      if (!tool.sideEffecting())
        throw new ProviderToolOperationException(
            ProviderToolOperationException.Code.UNSUPPORTED,
            "Home Assistant action must be declared side-effecting");
      homeAssistantPolicy.require(context, arguments);
    } catch (ProviderToolOperationException denied) {
      observe(
          provider,
          tool,
          context,
          false,
          "permission_denied",
          denied.getMessage(),
          null,
          System.nanoTime(),
          arguments);
      throw denied;
    }
  }

  private ProviderToolOperationException deniedHome(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      Map<String, Object> arguments,
      String message) {
    observe(
        provider,
        tool,
        context,
        false,
        "permission_denied",
        message,
        null,
        System.nanoTime(),
        arguments);
    return new ProviderToolOperationException(
        ProviderToolOperationException.Code.UNSUPPORTED, message);
  }

  private boolean matchesHomeApproval(
      ToolApproval approval,
      ZalavaProvider provider,
      InvocationContext context,
      Map<String, Object> arguments) {
    var recorded =
        new ApprovalBinding(
            approval.actorId(),
            approval.attributes().get("approvedModuleId"),
            approval.providerScope(),
            approval.attributes().get("accountRole"),
            approval.attributes().get("conversationId"),
            decoder.decode(approval.argumentsJson()));
    var invocation =
        new ApprovalBinding(
            context.actorId(),
            provider.descriptor().moduleId(),
            provider.descriptor().scope(),
            context.attributes().get("accountRole"),
            context.attributes().get("conversationId"),
            arguments);
    return recorded.matches(invocation);
  }

  private static boolean isHomeApproval(ToolApproval approval) {
    return "zalava-module-home-assistant".equals(approval.attributes().get("approvedModuleId"))
        && "home_assistant_execute_action".equals(approval.toolName());
  }

  private static ZalavaOperationResult alreadyExecuted(ToolApproval approval) {
    String outcome = approval.attributes().getOrDefault("approvalExecutionOutcome", "unknown");
    if ("succeeded".equals(outcome)) {
      return ZalavaOperationResult.success(
          Map.of(
              "status", "already_executed",
              "approvalRequestId", approval.requestId(),
              "message", "This approved operation already completed successfully."));
    }
    return ZalavaOperationResult.failure(
        Map.of(
            "status",
            "failed".equals(outcome) ? "execution_failed" : "execution_unknown",
            "approvalRequestId",
            approval.requestId(),
            "message",
            "The previous approved operation did not report success; check its outcome before retrying."));
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
    observe(provider, tool, context, success, errorType, errorMessage, result, startedAt, Map.of());
  }

  private void observe(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      boolean success,
      String errorType,
      String errorMessage,
      ZalavaOperationResult result,
      long startedAt,
      Map<String, Object> arguments) {
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
            (System.nanoTime() - startedAt) / 1_000_000,
            arguments);
    observers.forEach(observer -> observer.observe(observation));
  }

  private static InvocationContext confirmedContext(InvocationContext context, String requestId) {
    return new InvocationContext(
        context.actorId(), true, taskApprovedAttributes(context.attributes(), requestId));
  }

  private static InvocationContext approvedByToolPolicyContext(
      InvocationContext context, String requestId, String homeModuleId) {
    Map<String, String> approved = new LinkedHashMap<>(context.attributes());
    approved.remove(ToolInvocationObservation.HOME_AUDIT_MODULE);
    approved.remove(ToolInvocationObservation.HOME_AUDIT_EXECUTION);
    if (homeModuleId != null) {
      approved.put(ToolInvocationObservation.HOME_AUDIT_MODULE, homeModuleId);
      approved.put(ToolInvocationObservation.HOME_AUDIT_EXECUTION, UUID.randomUUID().toString());
    }
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

  private static ToolApproval approvalDecision(Supplier<ToolApproval> decision) {
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
