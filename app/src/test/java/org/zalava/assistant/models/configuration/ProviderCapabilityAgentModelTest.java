package org.zalava.assistant.models.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.zalava.assistant.agent.adapter.out.springai.SpringAiAgentModel;
import org.zalava.tasks.domain.Task;

class ProviderCapabilityAgentModelTest {
  @Test
  void chatOnlyProviderReceivesNoToolDefinitionsAndCannotStartToolTasks() {
    AtomicReference<Prompt> captured = new AtomicReference<>();
    ChatModel model =
        prompt -> {
          captured.set(prompt);
          return new ChatResponse(List.of(new Generation(new AssistantMessage("Answer"))));
        };
    var agent = new SpringAiAgentModel(ChatClient.builder(model).build(), null, false);
    assertThat(agent.conversational("chat", "Question", List.of(new Object()))).isEqualTo("Answer");
    assertThat(captured.get().getOptions()).isNotInstanceOf(ToolCallingChatOptions.class);
    captured.set(null);
    assertThat(agent.task("task", "Do work", List.of()))
        .satisfies(result -> assertThat(result.newStatus()).isEqualTo(Task.Status.failed));
    assertThat(captured.get()).isNull();
  }

  public record Answer(String text) {}

  @Test
  void chatOnlyProviderUsesTextStructuredConversion() {
    ChatModel model =
        prompt ->
            new ChatResponse(
                List.of(new Generation(new AssistantMessage("{\"text\":\"result\"}"))));
    var agent = new SpringAiAgentModel(ChatClient.builder(model).build(), null, false);
    assertThat(agent.structured("chat", "Question", List.of(), Answer.class).text())
        .isEqualTo("result");
  }
}
