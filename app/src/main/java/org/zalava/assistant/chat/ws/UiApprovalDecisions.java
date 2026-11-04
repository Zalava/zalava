package org.zalava.assistant.chat.ws;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.chat.application.DirectChatApprovals;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.web.ui.protocol.UiCommand;
import org.zalava.web.ui.protocol.UiEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Applies a UI approval intent through the existing owner-scoped Zalava authority. */
@Component
final class UiApprovalDecisions {
  private final AuthenticatedActorResolver actors;
  private final ActorTaskCommands tasks;
  private final ZalavaToolApprovalRequests approvals;
  private final ProviderToolOperations operations;
  private final ModelBoundary boundary;
  private final DirectChatApprovals directApprovals;
  private static final ObjectMapper JSON = new ObjectMapper();

  UiApprovalDecisions(
      AuthenticatedActorResolver actors,
      ActorTaskCommands tasks,
      ZalavaToolApprovalRequests approvals,
      ProviderToolOperations operations) {
    this(actors, tasks, approvals, operations, new ModelBoundary(1200, ""));
  }

  @Autowired
  UiApprovalDecisions(
      AuthenticatedActorResolver actors,
      ActorTaskCommands tasks,
      ZalavaToolApprovalRequests approvals,
      ProviderToolOperations operations,
      ModelBoundary boundary) {
    this.boundary = boundary;
    this.actors = actors;
    this.tasks = tasks;
    this.approvals = approvals;
    this.operations = operations;
    this.directApprovals = new DirectChatApprovals(approvals, operations);
  }

  List<UiEvent.DirectPermission> pendingDirect(String loginName) {
    Actor actor = actors.actorForLogin(loginName);
    AccountRole role = actors.roleForLogin(loginName);
    return directApprovals.pending(actor, role).stream()
        .map(
            entry ->
                new UiEvent.DirectPermission(
                    entry.requestId(),
                    entry.providerId() + "/" + entry.toolName(),
                    safeArguments(entry.arguments())))
        .toList();
  }

  synchronized String decideDirect(String loginName, UiCommand.DecideDirectApproval command) {
    Actor actor = actors.actorForLogin(loginName);
    return directApprovals.decide(
        actor,
        actors.roleForLogin(loginName),
        command.requestId(),
        command.decision() == UiCommand.DecideApproval.Decision.ALLOW_ONCE);
  }

  private String safeArguments(JsonNode arguments) {
    String preview = boundary.redact(JSON.writeValueAsString(sanitize(arguments)));
    return preview.length() > 1000 ? preview.substring(0, 1000) + "…" : preview;
  }

  private static Object sanitize(JsonNode arguments) {
    if (arguments.isObject()) {
      Map<String, Object> safe = new LinkedHashMap<>();
      for (var property : arguments.properties()) {
        String name = property.getKey();
        safe.put(
            name,
            name.toLowerCase(Locale.ROOT)
                    .matches(".*(password|secret|token|authorization|api.?key|credential).*")
                ? ModelBoundary.REDACTION
                : sanitize(property.getValue()));
      }
      return safe;
    }
    if (arguments.isArray()) {
      return StreamSupport.stream(arguments.spliterator(), false)
          .map(UiApprovalDecisions::sanitize)
          .toList();
    }
    return arguments;
  }

  Task decide(String loginName, UiCommand.DecideApproval command) {
    Actor actor = actors.actorForLogin(loginName);
    ActorTaskReference taskReference = new ActorTaskReference(command.jobId());
    Task task = tasks.get(actor, taskReference);
    if (task.getStatus() != Task.Status.awaiting_human_input) {
      throw new IllegalStateException("Job is not waiting for an approval decision");
    }
    ZalavaToolApprovalRequests.Entry approval =
        approvals.get(actor, taskReference, command.requestId());
    if (actors.roleForLogin(loginName) == AccountRole.MEMBER
        && !"MEMBER".equals(approval.attributes().get("accountRole"))) {
      throw new ZalavaToolApprovalRequests.NotFoundException(command.requestId());
    }
    switch (command.decision()) {
      case ALLOW_ONCE -> operations.allowUnscoped(command.requestId());
      case DENY -> operations.denyUnscoped(command.requestId());
    }
    if (!approvals.hasPending(actor, taskReference)) {
      tasks.resume(actor, taskReference);
    }
    return tasks.get(actor, taskReference);
  }
}
