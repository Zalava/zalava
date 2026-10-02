package org.zalava.assistant.agent.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.zalava.assistant.agent.application.port.out.StructuredOutputSchemaException;
import org.zalava.assistant.agent.application.port.out.StructuredRunEvidence;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.domain.Task;
import reactor.core.publisher.Flux;

class SpringAiAgentModelTest {

  @BeforeEach
  void clearStructuredEvidence() {
    StructuredRunEvidence.consumeOrDefault(0);
  }

  @Test
  void taskDisablesAutomaticToolCallingAdvisor() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    ChatResponse chatResponse = mock(ChatResponse.class);
    Generation generation = mock(Generation.class);
    AssistantMessage output = mock(AssistantMessage.class);
    when(chatClient.prompt("prompt")).thenReturn(request);
    when(request.tools(any(Object[].class))).thenReturn(request);
    when(request.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
        .thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.chatResponse()).thenReturn(chatResponse);
    when(chatResponse.getResult()).thenReturn(generation);
    when(generation.getOutput()).thenReturn(output);
    when(output.getToolCalls()).thenReturn(List.of());
    when(output.getText()).thenReturn("{\"newStatus\":\"completed\",\"feedback\":\"done\"}");

    assertThat(new SpringAiAgentModel(chatClient).task("task", "prompt", List.of()))
        .isEqualTo(new TaskAgent.Result(Task.Status.completed, "done"));

    ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisor =
        ArgumentCaptor.<Consumer<ChatClient.AdvisorSpec>>captor();
    verify(request, times(2)).advisors(advisor.capture());
    ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
    advisor.getAllValues().getLast().accept(advisorSpec);
    verify(advisorSpec)
        .param(ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false);
  }

  @Test
  void taskTranslatesRequestedToolCallThroughSpringToolCallingManager() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    ToolCallback callback = mock(ToolCallback.class);
    ToolDefinition definition =
        ToolDefinition.builder()
            .name("write_report")
            .description("writes report")
            .inputSchema("{}")
            .build();
    AssistantMessage output =
        AssistantMessage.builder()
            .content("")
            .toolCalls(
                List.of(new AssistantMessage.ToolCall("call-1", "function", "write_report", "{}")))
            .build();
    when(chatClient.prompt("prompt")).thenReturn(request);
    when(request.tools(any(Object[].class))).thenReturn(request);
    when(request.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
        .thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.chatResponse()).thenReturn(new ChatResponse(List.of(new Generation(output))));
    when(callback.getToolDefinition()).thenReturn(definition);
    when(callback.getToolMetadata()).thenReturn(ToolMetadata.builder().returnDirect(false).build());
    when(callback.call(eq("{}"), any(org.springframework.ai.chat.model.ToolContext.class)))
        .thenReturn("report written");

    TaskAgent.Result result =
        new SpringAiAgentModel(chatClient).task("task", "prompt", List.of(callback));

    assertThat(result.newStatus()).isEqualTo(Task.Status.in_progress);
    assertThat(result.feedback()).isNotBlank();
    verify(callback).call(eq("{}"), any(org.springframework.ai.chat.model.ToolContext.class));
  }

  @Test
  void structuredUsesValidationRetriesAndProviderNativeOutput() throws Exception {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    Result expected = new Result("sea");
    when(chatClient.prompt("prompt")).thenReturn(request);
    when(request.tools(any(Object[].class))).thenReturn(request);
    when(request.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
        .thenReturn(request);
    when(request.advisors(any(Advisor[].class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(eq(Result.class), any())).thenReturn(expected);

    Result actual =
        new SpringAiAgentModel(chatClient)
            .structured("conversation", "prompt", List.of(), Result.class);

    assertThat(actual).isEqualTo(expected);
    ArgumentCaptor<Advisor[]> advisors = ArgumentCaptor.forClass(Advisor[].class);
    verify(request).advisors(advisors.capture());
    assertThat(advisors.getValue()).hasSize(2);
    assertThat(advisors.getValue()[0]).isInstanceOf(StructuredOutputValidationAdvisor.class);
    Field repeats = StructuredOutputValidationAdvisor.class.getDeclaredField("maxRepeatAttempts");
    repeats.setAccessible(true);
    assertThat(repeats.getInt(advisors.getValue()[0])).isEqualTo(2);

    ArgumentCaptor<Consumer<ChatClient.EntityParamSpec>> output =
        ArgumentCaptor.<Consumer<ChatClient.EntityParamSpec>>captor();
    verify(response).entity(eq(Result.class), output.capture());
    ChatClient.EntityParamSpec parameters = mock(ChatClient.EntityParamSpec.class);
    when(parameters.useProviderStructuredOutput()).thenReturn(parameters);
    output.getValue().accept(parameters);
    verify(parameters).useProviderStructuredOutput();
    verify(parameters, never()).validateSchema();
  }

  @Test
  void structuredRetriesTwiceThroughTheRealAdvisorComposition() {
    ScriptedChatModel model = new ScriptedChatModel(ToolCallingChatOptions.builder().build());
    model.reply("{\"unexpected\":true}");
    SpringAiAgentModel agentModel = new SpringAiAgentModel(ChatClient.builder(model).build());

    assertThatThrownBy(
            () -> agentModel.structured("conversation", "prompt", List.of(), StrictResult.class))
        .isInstanceOf(StructuredOutputSchemaException.class)
        .hasMessageContaining("after 3 attempts");

    assertThat(model.calls()).as("one attempt plus exactly two retries").isEqualTo(3);
    assertThat(StructuredRunEvidence.consumeOrDefault(1))
        .as("every advisor attempt must be counted")
        .isEqualTo(3);
  }

  @Test
  void structuredSucceedsAfterOneRetryAndCountsBothAttempts() {
    ScriptedChatModel model = new ScriptedChatModel(ToolCallingChatOptions.builder().build());
    model.reply("not json");
    model.reply("{\"value\":\"recovered\"}");
    SpringAiAgentModel agentModel = new SpringAiAgentModel(ChatClient.builder(model).build());

    assertThat(agentModel.structured("conversation", "prompt", List.of(), Result.class))
        .isEqualTo(new Result("recovered"));
    assertThat(model.calls()).isEqualTo(2);
    assertThat(StructuredRunEvidence.consumeOrDefault(1)).isEqualTo(2);
  }

  @Test
  void conversationalDoesNotRecordStructuredAttemptEvidence() {
    ScriptedChatModel model = new ScriptedChatModel(ToolCallingChatOptions.builder().build());
    model.reply("reply");
    SpringAiAgentModel agentModel = new SpringAiAgentModel(ChatClient.builder(model).build());

    assertThat(agentModel.conversational("conversation", "prompt", List.of())).isEqualTo("reply");
    assertThat(StructuredRunEvidence.consumeOrDefault(7))
        .as("conversational turns must not touch structured evidence")
        .isEqualTo(7);
  }

  @Test
  void conversationalDoesNotApplyStructuredOutputConfiguration() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt("prompt")).thenReturn(request);
    when(request.tools(any(Object[].class))).thenReturn(request);
    when(request.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
        .thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.content()).thenReturn("reply");

    assertThat(
            new SpringAiAgentModel(chatClient).conversational("conversation", "prompt", List.of()))
        .isEqualTo("reply");
    verify(request).call();
  }

  @Test
  void conversationalNormalizesInvalidCallbackNamesBeforeCallingTheModel() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    ToolCallback callback = mock(ToolCallback.class);
    ToolDefinition definition =
        ToolDefinition.builder()
            .name("workspace.read:file")
            .description("Reads a workspace file")
            .inputSchema("{}")
            .build();
    when(chatClient.prompt("prompt")).thenReturn(request);
    when(request.tools(any(Object[].class))).thenReturn(request);
    when(request.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
        .thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.content()).thenReturn("reply");
    when(callback.getToolDefinition()).thenReturn(definition);
    when(callback.getToolMetadata()).thenReturn(ToolMetadata.builder().returnDirect(false).build());

    new SpringAiAgentModel(chatClient).conversational("conversation", "prompt", List.of(callback));

    ArgumentCaptor<Object[]> tools = ArgumentCaptor.forClass(Object[].class);
    verify(request).tools(tools.capture());
    ToolCallback modelCallback = (ToolCallback) tools.getValue()[0];
    assertThat(modelCallback.getToolDefinition().name()).matches("[A-Za-z0-9_-]+");
    assertThat(modelCallback.getToolDefinition().name()).isNotEqualTo("workspace.read:file");
    assertThat(modelCallback.getToolDefinition().description()).isEqualTo("Reads a workspace file");
    when(callback.call("{}")).thenReturn("file content");
    assertThat(modelCallback.call("{}")).isEqualTo("file content");
    verify(callback).call("{}");
  }

  private record Result(String value) {}

  private record StrictResult(String value, int count) {}

  private static final class ScriptedChatModel implements ChatModel {
    private final ChatOptions options;
    private final Deque<Function<Prompt, ChatResponse>> script = new ArrayDeque<>();
    private final AtomicInteger calls = new AtomicInteger();

    private ScriptedChatModel(ChatOptions options) {
      this.options = options;
    }

    ScriptedChatModel reply(String content) {
      script.add(
          prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
      return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
      calls.incrementAndGet();
      Function<Prompt, ChatResponse> next = script.size() > 1 ? script.poll() : script.peek();
      return next.apply(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getOptions() {
      return options;
    }

    int calls() {
      return calls.get();
    }
  }
}
