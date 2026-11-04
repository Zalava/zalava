package org.zalava.capabilities.operation.adapter.out.approval;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.zalava.api.InvocationContext;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.HostReadToolCatalog;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.channels.application.InvocationChannelContext;
import org.zalava.knowledge.application.KnowledgeReadGate;
import org.zalava.tasks.domain.TaskExecutionContext;
import tools.jackson.databind.ObjectMapper;

/** Applies Chat read confirmation without bypassing Knowledge's actor-scoped query. */
public final class KnowledgeChatReadGate implements KnowledgeReadGate {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ActorExecutionContext actors;
  private final TaskExecutionContext tasks;
  private final ChatToolConfirmation confirmation;
  private final ZalavaToolApprovalRequests approvals;

  public KnowledgeChatReadGate(
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
  public Optional<String> beforeRead(String operation, Map<String, Object> arguments) {
    var current = currentContext();
    if (current.isEmpty()) return Optional.empty();
    var tool = HostReadToolCatalog.tool(operation);
    if (tool == null) throw new IllegalArgumentException("Unknown Knowledge read");
    var context = current.get();
    var provider = HostReadToolCatalog.provider();
    if (!confirmation.requireFor(context, provider, tool).orElse(false)) return Optional.empty();
    var json = JSON.valueToTree(arguments);
    if (confirmation.mayConsumeReadApproval(context)
        && approvals.consumeChatReadDecision(context, provider, tool, json).isPresent())
      return Optional.empty();
    var request = approvals.create(provider, tool, context, json);
    return Optional.of(
        JSON.writeValueAsString(
            Map.of("status", "pending_approval", "approvalRequestId", request.requestId())));
  }

  @Override
  public boolean blockAutomaticRead() {
    return currentContext()
        .map(
            context ->
                HostReadToolCatalog.provider().listTools().stream()
                    .anyMatch(
                        tool ->
                            confirmation
                                .requireFor(context, HostReadToolCatalog.provider(), tool)
                                .orElse(false)))
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
    attributes.put("approvedModuleId", HostReadToolCatalog.MODULE);
    return Optional.of(
        new InvocationContext(principal.get().actor().accountId().toString(), false, attributes));
  }
}
