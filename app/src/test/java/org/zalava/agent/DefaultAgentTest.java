package org.zalava.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.zalava.agent.domain.AgentRun;
import org.zalava.memory.AgentMemory;
import org.zalava.memory.AgentMemoryDraft;
import org.zalava.memory.AgentMemoryScope;
import org.zalava.memory.AgentMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.tool.augment.AugmentedToolCallbackProvider;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

class DefaultAgentTest {

  private final ChatClient chatClient = mock(ChatClient.class);
  private final ChatClient.ChatClientRequestSpec request =
      mock(ChatClient.ChatClientRequestSpec.class);
  private final ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
  private final AgentRequestTools requestTools = mock(AgentRequestTools.class);
  private final InMemoryAgentRunRecorder runRecorder = new InMemoryAgentRunRecorder();
  private final AgentContextAssembler contextAssembler = new DefaultAgentContextAssembler(10);
  private final Object taskTool = new Object();
  private final Object seaCallback = new Object();
  private final DefaultAgent agent =
      new DefaultAgent(chatClient, requestTools, runRecorder, contextAssembler);

  @Test
  void appliesCompleteRequestToolSetToConversationalPrompt() {
    when(requestTools.resolve("conversation-1", "question"))
        .thenReturn(selection(List.of(taskTool, seaCallback)));
    when(chatClient.prompt("question")).thenReturn(request);
    when(request.tools(taskTool, seaCallback)).thenReturn(request);
    when(request.advisors(any(java.util.function.Consumer.class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.content()).thenReturn("answer");

    assertThat(agent.respondTo("conversation-1", "question")).isEqualTo("answer");

    verify(request).tools(taskTool, seaCallback);
    verify(requestTools).resolve("conversation-1", "question");
    verify(request).advisors(any(java.util.function.Consumer.class));
    assertThat(runRecorder.recent())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.conversationId()).isEqualTo("conversation-1");
              assertThat(record.promptType()).isEqualTo(AgentRun.PromptType.CONVERSATIONAL);
              assertThat(record.promptPreview()).isEqualTo("question");
              assertThat(record.selectedToolCount()).isEqualTo(2);
              assertThat(record.contextSourceCount()).isEqualTo(1);
              assertThat(record.contextCharacterBudget()).isEqualTo(10);
              assertThat(record.contextCharactersUsed()).isEqualTo(8);
              assertThat(record.contextSourceMetrics())
                  .singleElement()
                  .satisfies(
                      source -> {
                        assertThat(source.sourceType()).isEqualTo("user_prompt");
                        assertThat(source.charactersAvailable()).isEqualTo(8);
                        assertThat(source.charactersUsed()).isEqualTo(8);
                      });
              assertThat(record.status()).isEqualTo(AgentRun.Status.SUCCEEDED);
              assertThat(record.resultPreview()).isEqualTo("answer");
              assertThat(record.errorPreview()).isNull();
              assertThat(record.durationMillis()).isGreaterThanOrEqualTo(0);
            });
  }

  @Test
  void appliesCompleteRequestToolSetToStructuredPrompt() {
    Result expected = new Result("done");
    when(requestTools.resolve("task-1", "structured"))
        .thenReturn(selection(List.of(taskTool, seaCallback)));
    when(chatClient.prompt("structured")).thenReturn(request);
    when(request.tools(taskTool, seaCallback)).thenReturn(request);
    when(request.advisors(any(java.util.function.Consumer.class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(Result.class)).thenReturn(expected);

    assertThat(agent.prompt("task-1", "structured", Result.class)).isEqualTo(expected);

    verify(request).tools(taskTool, seaCallback);
    verify(requestTools).resolve("task-1", "structured");
    assertThat(runRecorder.recent())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.conversationId()).isEqualTo("task-1");
              assertThat(record.promptType()).isEqualTo(AgentRun.PromptType.STRUCTURED);
              assertThat(record.selectedToolCount()).isEqualTo(2);
              assertThat(record.contextSourceCount()).isEqualTo(1);
              assertThat(record.contextCharacterBudget()).isEqualTo(10);
              assertThat(record.contextCharactersUsed()).isEqualTo(10);
              assertThat(record.status()).isEqualTo(AgentRun.Status.SUCCEEDED);
              assertThat(record.resultPreview()).isEqualTo("Result[value=done]");
            });
  }

  @Test
  void recordsFailedPromptBeforeRethrowing() {
    when(requestTools.resolve("conversation-1", "broken")).thenReturn(selection(List.of(taskTool)));
    when(chatClient.prompt("broken")).thenReturn(request);
    when(request.tools(taskTool)).thenReturn(request);
    when(request.advisors(any(java.util.function.Consumer.class))).thenReturn(request);
    when(request.call()).thenThrow(new IllegalStateException("model failed"));

    assertThatThrownBy(() -> agent.respondTo("conversation-1", "broken"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("model failed");

    assertThat(runRecorder.recent())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.status()).isEqualTo(AgentRun.Status.FAILED);
              assertThat(record.resultPreview()).isNull();
              assertThat(record.errorPreview()).isEqualTo("IllegalStateException: model failed");
              assertThat(record.selectedToolCount()).isEqualTo(1);
              assertThat(record.contextCharactersUsed()).isEqualTo(6);
            });
  }

  @Test
  void sendsBudgetedPromptToModelAndKeepsOriginalPromptPreview() {
    when(requestTools.resolve("conversation-1", "question beyond budget"))
        .thenReturn(selection(List.of(taskTool)));
    when(chatClient.prompt("question b")).thenReturn(request);
    when(request.tools(taskTool)).thenReturn(request);
    when(request.advisors(any(java.util.function.Consumer.class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.content()).thenReturn("answer");

    assertThat(agent.respondTo("conversation-1", "question beyond budget")).isEqualTo("answer");

    verify(chatClient).prompt("question b");
    assertThat(runRecorder.recent())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.promptPreview()).isEqualTo("question beyond budget");
              assertThat(record.contextCharacterBudget()).isEqualTo(10);
              assertThat(record.contextCharactersUsed()).isEqualTo(10);
            });
  }

  @Test
  void addsSelectedSeaToolSummariesToPromptContext() {
    AgentContextAssembler assembler = new DefaultAgentContextAssembler(300);
    DefaultAgent agentWithToolContext =
        new DefaultAgent(chatClient, requestTools, runRecorder, assembler);
    AgentRequestTools.ToolSummary summary =
        new AgentRequestTools.ToolSummary(
            "shopping-list",
            "addItem",
            "Add an item to the active shopping list",
            true,
            List.of("sea_backed", "shopping-list"),
            java.util.Map.of("list", "active"));
    when(requestTools.resolve("conversation-1", "add apples"))
        .thenReturn(
            new AgentRequestTools.RequestToolSelection(
                List.of(taskTool, seaCallback), List.of(summary)));
    when(chatClient.prompt(org.mockito.ArgumentMatchers.contains("Selected SEA tool summaries:")))
        .thenReturn(request);
    when(request.tools(taskTool, seaCallback)).thenReturn(request);
    when(request.advisors(any(java.util.function.Consumer.class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.content()).thenReturn("answer");

    assertThat(agentWithToolContext.respondTo("conversation-1", "add apples")).isEqualTo("answer");

    assertThat(runRecorder.recent())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.contextSourceCount()).isEqualTo(2);
              assertThat(record.contextSourceMetrics())
                  .extracting(AgentRun.ContextSourceMetric::sourceType)
                  .containsExactly("user_prompt", "selected_tool_summaries");
            });
  }

  @Test
  void recordsSelectedToolDefinitionContext() {
    AgentContextAssembler assembler = new DefaultAgentContextAssembler(500);
    DefaultAgent agentWithToolContext =
        new DefaultAgent(chatClient, requestTools, runRecorder, assembler);
    AgentRequestTools.ToolSummary summary =
        new AgentRequestTools.ToolSummary(
            "shopping-list",
            "addItem",
            "Add an item to the active shopping list",
            true,
            List.of("sea_backed", "shopping-list"),
            java.util.Map.of("list", "active"));
    AgentRequestTools.ToolDefinitionSummary definition =
        new AgentRequestTools.ToolDefinitionSummary(
            "shopping-list",
            "addItem",
            "Add an item to the active shopping list",
            true,
            List.of("sea_backed", "shopping-list"),
            java.util.Map.of("list", "active"),
            java.util.Map.of("type", "object"),
            true);
    when(requestTools.resolve("conversation-1", "add apples"))
        .thenReturn(
            new AgentRequestTools.RequestToolSelection(
                List.of(taskTool, seaCallback), List.of(summary), List.of(definition)));
    when(chatClient.prompt(org.mockito.ArgumentMatchers.contains("Selected SEA tool definitions:")))
        .thenReturn(request);
    when(request.tools(taskTool, seaCallback)).thenReturn(request);
    when(request.advisors(any(java.util.function.Consumer.class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.content()).thenReturn("answer");

    assertThat(agentWithToolContext.respondTo("conversation-1", "add apples")).isEqualTo("answer");

    assertThat(runRecorder.recent())
        .singleElement()
        .satisfies(
            record ->
                assertThat(record.contextSourceMetrics())
                    .extracting(AgentRun.ContextSourceMetric::sourceType)
                    .containsExactly(
                        "user_prompt", "selected_tool_summaries", "selected_tool_definitions"));
  }

  @Test
  void recordsSelectedMemoryContext() {
    AgentContextAssembler assembler =
        new DefaultAgentContextAssembler(
            500,
            3,
            new SingleMemoryStore(
                new AgentMemory(
                    "memory-1",
                    AgentMemoryScope.PROJECT,
                    "SEA uses draft pull requests.",
                    Map.of("topic", "workflow"),
                    Instant.parse("2026-06-28T10:00:00Z"))));
    DefaultAgent agentWithMemoryContext =
        new DefaultAgent(chatClient, requestTools, runRecorder, assembler);
    when(requestTools.resolve("conversation-1", "draft pull request"))
        .thenReturn(
            new AgentRequestTools.RequestToolSelection(List.of(taskTool), List.of(), List.of()));
    when(chatClient.prompt(org.mockito.ArgumentMatchers.contains("Selected memories:")))
        .thenReturn(request);
    when(request.tools(taskTool)).thenReturn(request);
    when(request.advisors(any(java.util.function.Consumer.class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.content()).thenReturn("answer");

    assertThat(agentWithMemoryContext.respondTo("conversation-1", "draft pull request"))
        .isEqualTo("answer");

    assertThat(runRecorder.recent())
        .singleElement()
        .satisfies(
            record ->
                assertThat(record.contextSourceMetrics())
                    .extracting(AgentRun.ContextSourceMetric::sourceType)
                    .containsExactly("user_prompt", "selected_memories"));
  }

  @Test
  void springAiComposableToolCallingApisRemainAvailableForFutureManualControl() {
    assertThat(ToolCallingAdvisor.class).isNotNull();
    assertThat(StructuredOutputValidationAdvisor.class).isNotNull();
    assertThat(AugmentedToolCallbackProvider.class).isNotNull();
    assertThat(ToolSearchToolCallingAdvisor.class).isNotNull();

    ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class, RETURNS_SELF);
    AdvisorParams.toolCallingAdvisorAutoRegister(false).accept(advisorSpec);

    verify(advisorSpec)
        .param(ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false);
  }

  @Test
  void springAiToolSearchAdvisorCanUseSeaToolIndexBoundary() {
    ToolIndex toolIndex =
        new ToolIndex() {
          @Override
          public void indexTool(
              String sessionId,
              org.springframework.ai.tool.toolsearch.ToolReference toolReference) {}

          @Override
          public ToolSearchResponse search(ToolSearchRequest request) {
            return ToolSearchResponse.builder().build();
          }

          @Override
          public void clearIndex(String sessionId) {}
        };

    ToolSearchToolCallingAdvisor advisor =
        ToolSearchToolCallingAdvisor.builder().toolIndex(toolIndex).maxResults(5).build();

    assertThat(advisor).isInstanceOf(ToolCallingAdvisor.class);
  }

  private record Result(String value) {}

  private static AgentRequestTools.RequestToolSelection selection(List<Object> tools) {
    return new AgentRequestTools.RequestToolSelection(tools, List.of());
  }

  private record SingleMemoryStore(AgentMemory memory) implements AgentMemoryStore {

    @Override
    public AgentMemory remember(AgentMemoryDraft draft) {
      throw new UnsupportedOperationException("remember is not used by this test");
    }

    @Override
    public List<AgentMemory> recent(int limit) {
      throw new UnsupportedOperationException("recent is not used by this test");
    }

    @Override
    public List<AgentMemory> search(String query, int limit) {
      return List.of(memory).stream().limit(limit).toList();
    }
  }
}
