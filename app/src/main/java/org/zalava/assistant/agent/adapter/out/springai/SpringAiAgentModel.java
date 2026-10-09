package org.zalava.assistant.agent.adapter.out.springai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zalava.assistant.agent.adapter.out.system.AgentEnvironment;
import org.zalava.assistant.agent.application.port.out.AgentModel;
import org.zalava.assistant.agent.application.port.out.StructuredOutputSchemaException;
import org.zalava.assistant.agent.application.port.out.StructuredRunEvidence;
import org.zalava.tasks.application.BoundedTaskAgentLoop.ToolFailure;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.domain.Task;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public final class SpringAiAgentModel implements AgentModel {
  private final ChatClient chatClient;
  private final WorkspaceAgentPrompt systemPrompt;

  public SpringAiAgentModel(ChatClient chatClient) {
    this(chatClient, null);
  }

  @Autowired
  public SpringAiAgentModel(ChatClient chatClient, WorkspaceAgentPrompt systemPrompt) {
    this.chatClient = chatClient;
    this.systemPrompt = systemPrompt;
  }

  @Override
  public String conversational(String conversationId, String prompt, List<Object> tools) {
    return request(conversationId, prompt, tools).call().content();
  }

  /**
   * Streams the model response, forwarding raw content chunks to {@code deltas} and returning the
   * complete aggregated text. The flux is drained on the calling thread via {@code toIterable()},
   * so delta consumers (and any session writes they perform) run on the caller's thread instead of
   * reactor event-loop threads; tool callbacks inside the stream may still execute on other threads
   * and are handled by the tool-context bridge. Chunk pass-through semantics verified against the
   * Spring AI 2.0.0 aggregator (side-effect accumulation, elements passed through, no replay), so
   * each chunk arrives exactly once.
   */
  @Override
  public String conversational(
      String conversationId, String prompt, List<Object> tools, Consumer<String> deltas) {
    StringBuilder full = new StringBuilder();
    for (String chunk : request(conversationId, prompt, tools).stream().content().toIterable()) {
      if (chunk != null && !chunk.isEmpty()) {
        full.append(chunk);
        deltas.accept(chunk);
      }
    }
    return full.toString();
  }

  @Override
  public <T> T structured(
      String conversationId, String prompt, List<Object> tools, Class<T> resultType) {
    AtomicInteger attempts = StructuredRunEvidence.begin();
    try {
      return request(conversationId, prompt, tools)
          .advisors(
              StructuredOutputValidationAdvisor.builder()
                  .outputType(resultType)
                  .maxRepeatAttempts(2)
                  .build(),
              new StructuredAttemptCountingAdvisor(attempts))
          .call()
          .entity(resultType, structuredOutput -> structuredOutput.useProviderStructuredOutput());
    } catch (JacksonException failure) {
      throw new StructuredOutputSchemaException(
          "Structured output schema validation failed after " + attempts.get() + " attempts",
          failure);
    }
  }

  @Override
  public TaskAgent.Result task(String conversationId, String prompt, List<Object> tools) {
    ChatResponse response =
        request(conversationId, prompt, tools)
            .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
            .call()
            .chatResponse();
    if (response.getResult().getOutput().getToolCalls().isEmpty()) {
      try {
        return new ObjectMapper()
            .readValue(response.getResult().getOutput().getText(), TaskAgent.Result.class);
      } catch (RuntimeException failure) {
        return new TaskAgent.Result(
            Task.Status.failed, "Task model did not return a valid task result.");
      }
    }
    List<ToolCallback> callbacks = callbacks(tools);
    try {
      ToolExecutionResult toolResult =
          ToolCallingManager.builder()
              .toolCallbackResolver(new StaticToolCallbackResolver(callbacks))
              .build()
              .executeToolCalls(
                  new Prompt(
                      new UserMessage(prompt),
                      ToolCallingChatOptions.builder().toolCallbacks(callbacks).build()),
                  response);
      return new TaskAgent.Result(Task.Status.in_progress, toolFeedback(toolResult));
    } catch (RuntimeException failure) {
      throw new ToolFailure("tool execution failed: " + failure.getClass().getSimpleName());
    }
  }

  private static List<ToolCallback> callbacks(List<Object> tools) {
    return ModelSafeToolCallbacks.forTools(tools);
  }

  private static String toolFeedback(ToolExecutionResult toolResult) {
    String feedback =
        toolResult.conversationHistory().stream()
            .filter(ToolResponseMessage.class::isInstance)
            .map(ToolResponseMessage.class::cast)
            .flatMap(message -> message.getResponses().stream())
            .map(ToolResponseMessage.ToolResponse::responseData)
            .filter(text -> text != null && !text.isBlank())
            .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    return feedback.isBlank() ? "Tool execution finished; continue the task." : feedback;
  }

  private static final class StructuredAttemptCountingAdvisor implements CallAdvisor {
    private final AtomicInteger attempts;

    private StructuredAttemptCountingAdvisor(AtomicInteger attempts) {
      this.attempts = attempts;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
      attempts.incrementAndGet();
      return chain.nextCall(request);
    }

    @Override
    public String getName() {
      return "zalava-structured-attempt-counter";
    }

    @Override
    public int getOrder() {
      return Integer.MAX_VALUE - 1;
    }
  }

  private ChatClient.ChatClientRequestSpec request(
      String conversationId, String prompt, List<Object> tools) {
    var request = chatClient.prompt(prompt);
    if (systemPrompt != null) {
      request.system(
          p ->
              p.text(systemPrompt.text())
                  .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info()));
    }
    return request
        .tools(callbacks(tools).toArray())
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
  }
}
