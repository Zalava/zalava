package org.zalava.support;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class SeaComponentTestConfiguration {

  @Bean
  @Primary
  ChatModel deterministicChatModel() {
    return new DeterministicChatModel();
  }

  private static final class DeterministicChatModel implements ChatModel {

    private final ChatOptions options = ToolCallingChatOptions.builder().build();

    @Override
    public ChatResponse call(Prompt prompt) {
      return new ChatResponse(
          List.of(
              new Generation(new AssistantMessage("Hello from the shared component test model."))));
    }

    @Override
    public ChatOptions getOptions() {
      return options;
    }
  }
}
