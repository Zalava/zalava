package org.zalava.agent.adapter.out.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.AgentRequestTools;
import org.zalava.tools.ActorTaskCreationContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Covers the policy adapter that converts a request-scoped tool resolution into the agent-domain
 * selection, including the anonymous-principal path and every mapped summary/definition field.
 */
class AgentRequestToolSelectorTest {

  @Test
  void mapsResolvedSummariesAndDefinitionsIntoTheAgentDomainSelection() {
    AgentRequestTools requestTools = mock(AgentRequestTools.class);
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    var selection =
        new AgentRequestTools.RequestToolSelection(
            List.of(new Object()),
            List.of(
                new AgentRequestTools.ToolSummary(
                    "provider",
                    "tool",
                    "description",
                    true,
                    List.of("sea_backed"),
                    Map.of("scope", "value"))),
            List.of(
                new AgentRequestTools.ToolDefinitionSummary(
                    "provider",
                    "tool",
                    "description",
                    true,
                    List.of("sea_backed"),
                    Map.of("scope", "value"),
                    Map.of("type", "object"),
                    false)));
    when(requestTools.resolve(eq("conversation-1"), eq("input"), eq(AccountRole.MEMBER)))
        .thenReturn(selection);
    var selector =
        new AgentRequestToolSelector(
            requestTools,
            actorExecution,
            new StreamingToolContextBridge(actorExecution, new ActorTaskCreationContext()));

    Actor actor = new Actor(org.zalava.accounts.domain.AccountId.newId());
    var result =
        actorExecution.call(
            actor, AccountRole.MEMBER, () -> selector.select("conversation-1", "input"));

    // With an active principal the tools are bound for streamed turns: each entry becomes a
    // context-restoring provider instead of the raw tool object.
    assertThat(result.tools()).hasSize(1).first().isInstanceOf(ToolCallbackProvider.class);
    assertThat(result.toolSummaries())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.providerId()).isEqualTo("provider");
              assertThat(summary.toolName()).isEqualTo("tool");
              assertThat(summary.description()).isEqualTo("description");
              assertThat(summary.sideEffecting()).isTrue();
              assertThat(summary.policyTags()).containsExactly("sea_backed");
              assertThat(summary.scope()).containsEntry("scope", "value");
            });
    assertThat(result.toolDefinitions())
        .singleElement()
        .satisfies(
            definition -> {
              assertThat(definition.available()).isFalse();
              assertThat(definition.inputSchema()).containsEntry("type", "object");
            });
  }

  @Test
  void anAbsentPrincipalMapsToANullRole() {
    AgentRequestTools requestTools = mock(AgentRequestTools.class);
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    var selector =
        new AgentRequestToolSelector(
            requestTools,
            actorExecution,
            new StreamingToolContextBridge(actorExecution, new ActorTaskCreationContext()));
    var emptySelection =
        new AgentRequestTools.RequestToolSelection(List.of(), List.of(), List.of());
    when(requestTools.resolve("conversation-1", "input", null)).thenReturn(emptySelection);

    var result = selector.select("conversation-1", "input");

    assertThat(result.tools()).isEmpty();
    assertThat(result.toolSummaries()).isEmpty();
    assertThat(result.toolDefinitions()).isEmpty();
  }
}
