package org.zalava.assistant.channels.application;

import java.util.Arrays;
import java.util.Optional;
import org.zalava.assistant.channels.application.port.in.ChannelApprovalCommands;
import org.zalava.assistant.channels.application.port.out.ChannelApprovalStore;
import org.zalava.assistant.channels.application.port.out.ChannelProviderOperations;
import org.zalava.assistant.channels.application.port.out.ChannelTasks;
import org.zalava.assistant.channels.domain.ChannelApproval;

public final class DefaultChannelApprovalCommands implements ChannelApprovalCommands {
  private static final String PREFIX = "/zalava";
  private static final String LAST_REQUEST = "last";

  private final ChannelApprovalStore approvals;
  private final ChannelProviderOperations operations;
  private final ChannelTasks tasks;

  public DefaultChannelApprovalCommands(
      ChannelApprovalStore approvals, ChannelProviderOperations operations, ChannelTasks tasks) {
    this.approvals = approvals;
    this.operations = operations;
    this.tasks = tasks;
  }

  @Override
  public Optional<String> handle(String message) {
    ParsedCommand command = parse(message).orElse(null);
    if (command == null) return Optional.empty();
    if (command.action() == Action.INVALID)
      return Optional.of("Zalava could not understand that approval command.");
    try {
      if (command.action() == Action.LIST_POLICIES) return Optional.of(policyList());
      if (command.action() == Action.REVOKE_POLICY) {
        ChannelApproval policy = approvals.revokeToolPolicy(command.requestId());
        return Optional.of(
            "Zalava revoked durable policy %s/%s (%s)."
                .formatted(policy.providerId(), policy.toolName(), policy.requestId()));
      }
      String requestId = resolveRequestId(command.requestId());
      ChannelApproval request = approvals.get(requestId);
      if (!request.isTaskScoped()) return Optional.of(handleUnscoped(command.action(), request));
      if (!tasks.isAwaitingHumanInput(request.taskReference())) {
        return Optional.of(
            "Zalava cannot decide that approval because the job is not waiting for input.");
      }
      ChannelApproval decided =
          switch (command.action()) {
            case APPROVE -> approvals.allow(requestId, request.taskReference());
            case ALWAYS_ALLOW_TOOL -> approvals.allowTool(requestId, request.taskReference());
            case DENY -> approvals.deny(requestId, request.taskReference());
            case LIST_POLICIES, REVOKE_POLICY ->
                throw new IllegalArgumentException("Invalid Zalava approval command");
            case INVALID -> throw new IllegalArgumentException("Invalid Zalava approval command");
          };
      boolean resumed = false;
      if (!approvals.recent().stream()
          .anyMatch(
              entry ->
                  request.taskReference().equals(entry.taskReference())
                      && entry.decision() == ChannelApproval.Decision.PENDING)) {
        tasks.resume(request.taskReference());
        resumed = true;
      }
      return Optional.of(response(command.action(), decided, request.taskReference(), resumed));
    } catch (NotFoundException ex) {
      return Optional.of("Zalava approval request not found: " + command.requestId());
    } catch (AlreadyDecidedException ex) {
      return Optional.of("Zalava approval request was already decided: " + command.requestId());
    } catch (ChannelProviderOperationException ex) {
      return Optional.of(
          switch (ex.code()) {
            case APPROVAL_NOT_FOUND -> "Zalava approval request not found: " + command.requestId();
            case APPROVAL_CONFLICT ->
                "Zalava approval request was already decided: " + command.requestId();
            case OTHER -> "Zalava could not complete that approval command: " + ex.getMessage();
          });
    } catch (IllegalArgumentException ex) {
      return Optional.of("Zalava could not understand that approval command.");
    } catch (RuntimeException ex) {
      return Optional.of("Zalava could not complete that approval command: " + ex.getMessage());
    }
  }

  private String resolveRequestId(String requestId) {
    if (!LAST_REQUEST.equalsIgnoreCase(requestId)) return requestId;
    return approvals.recent().stream()
        .filter(entry -> entry.decision() == ChannelApproval.Decision.PENDING)
        .findFirst()
        .map(ChannelApproval::requestId)
        .orElseThrow(() -> new NotFoundException(LAST_REQUEST));
  }

  private String policyList() {
    var policies = approvals.activeToolPolicies();
    if (policies.isEmpty()) return "Zalava has no active durable tool policies.";
    String listed =
        policies.stream()
            .limit(10)
            .map(
                policy ->
                    "%s/%s (%s)"
                        .formatted(policy.providerId(), policy.toolName(), policy.requestId()))
            .reduce((left, right) -> left + "; " + right)
            .orElseThrow();
    String suffix = policies.size() > 10 ? "; additional policies omitted" : "";
    return "Zalava active durable tool policies: " + listed + suffix;
  }

  private String handleUnscoped(Action action, ChannelApproval request) {
    switch (action) {
      case APPROVE -> operations.allowUnscoped(request.requestId());
      case ALWAYS_ALLOW_TOOL -> operations.allowUnscopedTool(request.requestId());
      case DENY -> operations.denyUnscoped(request.requestId());
      case INVALID -> throw new IllegalArgumentException("Invalid action");
    }
    return response(action, request);
  }

  private static Optional<ParsedCommand> parse(String message) {
    if (message == null || message.isBlank()) return Optional.empty();
    String[] parts =
        Arrays.stream(message.trim().split("\\s+"))
            .filter(part -> !part.isBlank())
            .toArray(String[]::new);
    if (parts.length == 0 || !PREFIX.equalsIgnoreCase(parts[0])) return Optional.empty();
    if (parts.length == 2 && "policies".equalsIgnoreCase(parts[1])) {
      return Optional.of(new ParsedCommand(Action.LIST_POLICIES, ""));
    }
    if (parts.length != 3) return Optional.of(new ParsedCommand(Action.INVALID, ""));
    Action action =
        switch (parts[1].toLowerCase()) {
          case "approve" -> Action.APPROVE;
          case "always-allow-tool" -> Action.ALWAYS_ALLOW_TOOL;
          case "deny" -> Action.DENY;
          case "revoke-policy" -> Action.REVOKE_POLICY;
          default -> Action.INVALID;
        };
    return Optional.of(new ParsedCommand(action, parts[2]));
  }

  private static String response(
      Action action, ChannelApproval request, String reference, boolean resumed) {
    String continuation =
        resumed
            ? "The job has been queued to continue."
            : "The job is still waiting for another approval.";
    return "%s for %s/%s. %s Review: /jobs/%s"
        .formatted(
            decision(action), request.providerId(), request.toolName(), continuation, reference);
  }

  private static String response(Action action, ChannelApproval request) {
    String continuation =
        action == Action.DENY ? "The tool was not run." : "The approved tool has run.";
    return "%s for %s/%s. %s"
        .formatted(decision(action), request.providerId(), request.toolName(), continuation);
  }

  private static String decision(Action action) {
    return switch (action) {
      case APPROVE -> "Approval granted once";
      case ALWAYS_ALLOW_TOOL -> "Tool approval policy saved";
      case DENY -> "Approval denied";
      case LIST_POLICIES, REVOKE_POLICY -> throw new IllegalArgumentException("Invalid action");
      case INVALID -> throw new IllegalArgumentException("Invalid action");
    };
  }

  public static final class NotFoundException extends RuntimeException {
    public NotFoundException(String id) {
      super(id);
    }
  }

  public static final class AlreadyDecidedException extends RuntimeException {
    public AlreadyDecidedException(String id) {
      super(id);
    }
  }

  private enum Action {
    APPROVE,
    ALWAYS_ALLOW_TOOL,
    DENY,
    LIST_POLICIES,
    REVOKE_POLICY,
    INVALID
  }

  private record ParsedCommand(Action action, String requestId) {}
}
