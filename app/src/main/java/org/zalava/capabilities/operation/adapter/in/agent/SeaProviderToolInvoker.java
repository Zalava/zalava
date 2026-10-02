package org.zalava.capabilities.operation.adapter.in.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.assistant.agent.ConversationChannelContext;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.zalava.tasks.domain.TaskReference;

public final class SeaProviderToolInvoker {

  private final ProviderToolOperations operations;
  private final TaskExecutionContext taskExecutionContext;
  private final ActorExecutionContext actorExecutionContext;

  public SeaProviderToolInvoker(
      ProviderToolOperations operations, TaskExecutionContext taskExecutionContext) {
    this(operations, taskExecutionContext, null);
  }

  public SeaProviderToolInvoker(
      ProviderToolOperations operations,
      TaskExecutionContext taskExecutionContext,
      ActorExecutionContext actorExecutionContext) {
    this.operations = operations;
    this.taskExecutionContext = taskExecutionContext;
    this.actorExecutionContext = actorExecutionContext;
  }

  public ZalavaOperationResult invoke(
      String providerId, String toolName, String argumentsJson, String source) {
    Optional<TaskReference> taskReference = taskExecutionContext.currentTaskReference();
    Optional<ActorTaskExecutionReference> actorTaskReference =
        taskExecutionContext.currentActorTaskReference();
    ProviderToolOperations.ToolInvocationOutcome outcome;
    try {
      outcome =
          operations.invoke(
              ProviderToolOperations.ToolInvocationCommand.task(
                  providerId,
                  toolName,
                  argumentsJson,
                  invocationContext(source, taskReference, actorTaskReference),
                  taskReference.orElse(null)));
    } catch (ProviderToolOperationException ex) {
      throw agentFailure(ex);
    }
    return switch (outcome.status()) {
      case EXECUTED -> outcome.result();
      case DENIED ->
          ZalavaOperationResult.failure(
              Map.of("status", "denied", "approvalRequestId", outcome.approval().requestId()));
      case PENDING_APPROVAL ->
          ZalavaOperationResult.failure(
              Map.of(
                  "status",
                  "pending_approval",
                  "approvalRequestId",
                  outcome.approval().requestId(),
                  "jobReference",
                  jobReference(outcome, actorTaskReference)));
    };
  }

  private InvocationContext invocationContext(
      String source,
      Optional<TaskReference> taskReference,
      Optional<ActorTaskExecutionReference> actorTaskReference) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("source", source);
    ConversationChannelContext.current()
        .ifPresent(channel -> attributes.put("permissionPolicyChannel", channel));
    taskReference.ifPresent(reference -> attributes.put("taskReference", reference.path()));
    actorTaskReference.ifPresent(
        reference ->
            attributes.put(SeaToolApprovalRequests.ACTOR_TASK_REFERENCE, reference.encode()));
    if (actorExecutionContext != null) {
      var principal = actorExecutionContext.currentPrincipal();
      if (principal.isPresent()) {
        attributes.put("accountRole", principal.get().role().name());
        return new InvocationContext(
            principal.get().actor().accountId().toString(), false, attributes);
      }
    }
    return new InvocationContext("agent", false, attributes);
  }

  private static String jobReference(
      ProviderToolOperations.ToolInvocationOutcome outcome,
      Optional<ActorTaskExecutionReference> actorTaskReference) {
    if (actorTaskReference.isPresent()) return actorTaskReference.get().taskReference().value();
    return outcome.approval().taskReference() == null
        ? ""
        : outcome.approval().taskReference().path();
  }

  private static RuntimeException agentFailure(ProviderToolOperationException exception) {
    return switch (exception.code()) {
      case PROVIDER_NOT_FOUND, TOOL_NOT_FOUND, VALIDATION ->
          new IllegalArgumentException(exception.getMessage(), exception);
      case UNSUPPORTED -> new UnsupportedOperationException(exception.getMessage(), exception);
      case EXECUTION -> exception.getCause() instanceof RuntimeException cause ? cause : exception;
      case APPROVAL_NOT_FOUND, APPROVAL_CONFLICT -> exception;
    };
  }
}
