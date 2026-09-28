package org.zalava.agent.adapter.out.policy;

import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.agent.AgentRequestTools;
import org.zalava.agent.application.port.out.AgentToolSelector;
import org.zalava.agent.domain.AgentToolSelection;
import org.springframework.stereotype.Component;

@Component
public final class AgentRequestToolSelector implements AgentToolSelector {
  private final AgentRequestTools requestTools;
  private final ActorExecutionContext actorExecution;
  private final StreamingToolContextBridge streamBridge;

  public AgentRequestToolSelector(
      AgentRequestTools requestTools,
      ActorExecutionContext actorExecution,
      StreamingToolContextBridge streamBridge) {
    this.requestTools = requestTools;
    this.actorExecution = actorExecution;
    this.streamBridge = streamBridge;
  }

  @Override
  public AgentToolSelection select(String conversationId, String input) {
    var role = actorExecution.currentPrincipal().map(value -> value.role()).orElse(null);
    AgentRequestTools.RequestToolSelection selection =
        requestTools.resolve(conversationId, input, role);
    // Bind the current ThreadLocal contexts onto the tool objects so streamed turns keep actor
    // policy/scope and job capture on reactor threads. A null snapshot (no active principal —
    // legacy/system callers) leaves the tools untouched.
    StreamingToolContextBridge.Snapshot snapshot = streamBridge.snapshot();
    return new AgentToolSelection(
        streamBridge.bind(selection.tools(), snapshot),
        selection.toolSummaries().stream()
            .map(
                summary ->
                    new AgentToolSelection.ToolSummary(
                        summary.providerId(),
                        summary.toolName(),
                        summary.description(),
                        summary.sideEffecting(),
                        summary.policyTags(),
                        summary.scope()))
            .toList(),
        selection.toolDefinitions().stream()
            .map(
                definition ->
                    new AgentToolSelection.ToolDefinitionSummary(
                        definition.providerId(),
                        definition.toolName(),
                        definition.description(),
                        definition.sideEffecting(),
                        definition.policyTags(),
                        definition.scope(),
                        definition.inputSchema(),
                        definition.available()))
            .toList());
  }
}
