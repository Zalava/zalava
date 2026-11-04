package org.zalava.capabilities.operation.adapter.out.approval;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.zalava.api.InvocationContext;
import org.zalava.assistant.agent.application.port.out.HostCallbackPolicy;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.HostCallbackCatalog;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.channels.application.InvocationChannelContext;
import org.zalava.tasks.domain.TaskExecutionContext;
import tools.jackson.databind.ObjectMapper;

/** Defers an explicitly classified host callback for a one-use Chat approval. */
public final class HostCallbackChatPolicy implements HostCallbackPolicy {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ActorExecutionContext actors;
  private final TaskExecutionContext tasks;
  private final ChatToolConfirmation confirmation;
  private final ZalavaToolApprovalRequests approvals;

  public HostCallbackChatPolicy(
      ActorExecutionContext actors,
      TaskExecutionContext tasks,
      ChatToolConfirmation confirmation,
      ZalavaToolApprovalRequests approvals) {
    this.actors = actors;
    this.tasks = tasks;
    this.confirmation = confirmation;
    this.approvals = approvals;
  }

  @Override
  public Optional<String> beforeCall(String name, String input) {
    var tool = HostCallbackCatalog.tool(name);
    if (tool == null) return Optional.empty();
    var context = currentContext();
    if (context.isEmpty()) return Optional.empty();
    var invocation = context.get();
    var provider = HostCallbackCatalog.provider();
    if (!confirmation.requireFor(invocation, provider, tool).orElse(false)) return Optional.empty();
    var arguments = JSON.readTree(input);
    if (confirmation.mayConsumeReadApproval(invocation)
        && approvals.consumeChatHostDecision(invocation, provider, tool, arguments).isPresent())
      return Optional.empty();
    var request = approvals.create(provider, tool, invocation, arguments);
    return Optional.of(
        JSON.writeValueAsString(
            Map.of("status", "pending_approval", "approvalRequestId", request.requestId())));
  }

  @Override
  public boolean blockAutomaticContent(String name) {
    var tool = HostCallbackCatalog.tool(name);
    return "Skill".equals(name)
        && tool != null
        && currentContext()
            .map(
                context ->
                    confirmation
                        .requireFor(context, HostCallbackCatalog.provider(), tool)
                        .orElse(false))
            .orElse(false);
  }

  private Optional<InvocationContext> currentContext() {
    var principal = actors.currentPrincipal();
    var conversation = InvocationChannelContext.conversation();
    if (principal.isEmpty()
        || conversation.isEmpty()
        || !"web".equals(InvocationChannelContext.current().orElse(""))
        || tasks.currentTaskReference().isPresent()
        || tasks.currentActorTaskReference().isPresent()) return Optional.empty();
    var attributes = new LinkedHashMap<String, String>();
    attributes.put("source", "spring-ai-callback");
    attributes.put("permissionPolicyChannel", "web");
    attributes.put("conversationId", conversation.get());
    attributes.put("accountRole", principal.get().role().name());
    attributes.put("approvedModuleId", HostCallbackCatalog.MODULE);
    attributes.put("hostCallbackRetry", "true");
    return Optional.of(
        new InvocationContext(principal.get().actor().accountId().toString(), false, attributes));
  }
}
