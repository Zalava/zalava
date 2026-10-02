package org.zalava.assistant.agent;

import java.time.Instant;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.zalava.assistant.agent.application.port.in.AgentExecution;
import org.zalava.assistant.agent.application.port.out.AgentModel;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.domain.AgentContext;
import org.zalava.assistant.agent.domain.AgentToolSelection;
import org.zalava.tasks.application.port.out.TaskAgent;

/**
 * Compatibility facade retained while task, chat, and channel callers migrate to the execution
 * port.
 */
public final class DefaultAgent implements Agent {
  private final AgentExecution execution;

  public DefaultAgent(AgentExecution execution) {
    this.execution = execution;
  }

  /**
   * @deprecated Test compatibility for the pre-port constructor; production wiring uses {@link
   *     AgentExecution}.
   */
  @Deprecated(forRemoval = false)
  public DefaultAgent(
      ChatClient chatClient,
      AgentRequestTools requestTools,
      AgentRunStore runStore,
      org.zalava.assistant.agent.AgentContextAssembler legacyContextAssembler) {
    this(
        new org.zalava.assistant.agent.application.DefaultAgentExecution(
            new AgentModel() {
              @Override
              public String conversational(
                  String conversationId, String prompt, java.util.List<Object> tools) {
                return chatClient
                    .prompt(prompt)
                    .tools(tools.toArray())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
              }

              @Override
              public <T> T structured(
                  String conversationId,
                  String prompt,
                  java.util.List<Object> tools,
                  Class<T> resultType) {
                return chatClient
                    .prompt(prompt)
                    .tools(tools.toArray())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(resultType);
              }
            },
            (conversationId, input) -> selection(requestTools.resolve(conversationId, input)),
            (input, selection) ->
                context(legacyContextAssembler.assemble(input, legacySelection(selection))),
            runStore,
            Instant::now,
            () -> java.util.UUID.randomUUID().toString()));
  }

  private static AgentToolSelection selection(AgentRequestTools.RequestToolSelection selection) {
    return new AgentToolSelection(
        selection.tools(),
        selection.toolSummaries().stream()
            .map(
                value ->
                    new AgentToolSelection.ToolSummary(
                        value.providerId(),
                        value.toolName(),
                        value.description(),
                        value.sideEffecting(),
                        value.policyTags(),
                        value.scope()))
            .toList(),
        selection.toolDefinitions().stream()
            .map(
                value ->
                    new AgentToolSelection.ToolDefinitionSummary(
                        value.providerId(),
                        value.toolName(),
                        value.description(),
                        value.sideEffecting(),
                        value.policyTags(),
                        value.scope(),
                        value.inputSchema(),
                        value.available()))
            .toList());
  }

  private static AgentContext context(AgentContextAssembly context) {
    return new AgentContext(
        context.prompt(),
        context.sourceCount(),
        context.characterBudget(),
        context.charactersUsed(),
        context.sourceMetrics().stream()
            .map(
                metric ->
                    new AgentContext.SourceMetric(
                        metric.sourceType(), metric.charactersAvailable(), metric.charactersUsed()))
            .toList());
  }

  private static AgentRequestTools.RequestToolSelection legacySelection(
      AgentToolSelection selection) {
    return new AgentRequestTools.RequestToolSelection(
        selection.tools(),
        selection.toolSummaries().stream()
            .map(
                value ->
                    new AgentRequestTools.ToolSummary(
                        value.providerId(),
                        value.toolName(),
                        value.description(),
                        value.sideEffecting(),
                        value.policyTags(),
                        value.scope()))
            .toList(),
        selection.toolDefinitions().stream()
            .map(
                value ->
                    new AgentRequestTools.ToolDefinitionSummary(
                        value.providerId(),
                        value.toolName(),
                        value.description(),
                        value.sideEffecting(),
                        value.policyTags(),
                        value.scope(),
                        value.inputSchema(),
                        value.available()))
            .toList());
  }

  @Override
  public String respondTo(String conversationId, String question) {
    return execution.respondTo(conversationId, question);
  }

  @Override
  public <T> T prompt(String conversationId, String input, Class<T> result) {
    return execution.prompt(conversationId, input, result);
  }

  @Override
  public TaskAgent.Result task(String conversationId, String input) {
    return execution.task(conversationId, input);
  }
}
