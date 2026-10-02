package org.zalava.assistant.models.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;

class AnthropticClaudeCodeConfigurationTest {

  private final AnthropticClaudeCodeConfiguration configuration =
      new AnthropticClaudeCodeConfiguration();

  @Test
  void placeholderMatchesTheClaudeCodeTokenContract() {
    assertThat(AnthropticClaudeCodeConfiguration.CLAUDE_CODE_OATH_TOKEN_PLACEHOLDER)
        .isEqualTo("<claude-code-bearer-token>");
  }

  @Test
  void buildsAClientBackedChatModelFromConnectionProperties() {
    AnthropicConnectionProperties connection = new AnthropicConnectionProperties();
    connection.setApiKey(AnthropticClaudeCodeConfiguration.CLAUDE_CODE_OATH_TOKEN_PLACEHOLDER);
    connection.setBaseUrl("https://api.anthropic.com");
    connection.setTimeout(Duration.ofSeconds(30));
    connection.setMaxRetries(2);
    connection.setCustomHeaders(Map.of("x-test", "value"));

    var chatModel =
        configuration.anthropicChatModel(
            connection,
            new org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties(),
            new org.springframework.beans.factory.ObjectProvider<>() {
              @Override
              public io.micrometer.observation.ObservationRegistry getObject() {
                return io.micrometer.observation.ObservationRegistry.NOOP;
              }

              @Override
              public io.micrometer.observation.ObservationRegistry getIfAvailable() {
                return io.micrometer.observation.ObservationRegistry.NOOP;
              }

              @Override
              public io.micrometer.observation.ObservationRegistry getIfUnique() {
                return io.micrometer.observation.ObservationRegistry.NOOP;
              }

              @Override
              public io.micrometer.observation.ObservationRegistry getObject(Object... args) {
                return io.micrometer.observation.ObservationRegistry.NOOP;
              }
            },
            new org.springframework.beans.factory.ObjectProvider<>() {
              @Override
              public org.springframework.ai.chat.observation.ChatModelObservationConvention
                  getObject() {
                return null;
              }

              @Override
              public org.springframework.ai.chat.observation.ChatModelObservationConvention
                  getIfAvailable() {
                return null;
              }

              @Override
              public org.springframework.ai.chat.observation.ChatModelObservationConvention
                  getIfUnique() {
                return null;
              }

              @Override
              public org.springframework.ai.chat.observation.ChatModelObservationConvention
                  getObject(Object... args) {
                return null;
              }
            });

    assertThat(chatModel).isNotNull();
    assertThat(chatModel.getOptions()).isInstanceOf(AnthropicChatOptions.class);
    AnthropicChatOptions options = (AnthropicChatOptions) chatModel.getOptions();
    assertThat(options.getApiKey())
        .isEqualTo(AnthropticClaudeCodeConfiguration.CLAUDE_CODE_OATH_TOKEN_PLACEHOLDER);
    assertThat(options.getBaseUrl()).isEqualTo("https://api.anthropic.com");
    assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(options.getMaxRetries()).isEqualTo(2);
    assertThat(options.getCustomHeaders()).containsEntry("x-test", "value");
  }

  @Test
  void buildsAModelWithOnlyDefaultConnectionSettings() {
    AnthropicConnectionProperties connection = new AnthropicConnectionProperties();
    connection.setApiKey(AnthropticClaudeCodeConfiguration.CLAUDE_CODE_OATH_TOKEN_PLACEHOLDER);

    var chatModel =
        configuration.anthropicChatModel(
            connection,
            new org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties(),
            emptyObservationRegistry(),
            emptyObservationConvention());

    assertThat(chatModel).isNotNull();
    AnthropicChatOptions options = (AnthropicChatOptions) chatModel.getOptions();
    assertThat(options.getApiKey()).isNotBlank();
  }

  private org.springframework.beans.factory.ObjectProvider<
          io.micrometer.observation.ObservationRegistry>
      emptyObservationRegistry() {
    return new org.springframework.beans.factory.ObjectProvider<>() {
      @Override
      public io.micrometer.observation.ObservationRegistry getObject() {
        return io.micrometer.observation.ObservationRegistry.NOOP;
      }

      @Override
      public io.micrometer.observation.ObservationRegistry getIfAvailable() {
        return io.micrometer.observation.ObservationRegistry.NOOP;
      }

      @Override
      public io.micrometer.observation.ObservationRegistry getIfUnique() {
        return io.micrometer.observation.ObservationRegistry.NOOP;
      }

      @Override
      public io.micrometer.observation.ObservationRegistry getObject(Object... args) {
        return io.micrometer.observation.ObservationRegistry.NOOP;
      }
    };
  }

  private org.springframework.beans.factory.ObjectProvider<
          org.springframework.ai.chat.observation.ChatModelObservationConvention>
      emptyObservationConvention() {
    return new org.springframework.beans.factory.ObjectProvider<>() {
      @Override
      public org.springframework.ai.chat.observation.ChatModelObservationConvention getObject() {
        return null;
      }

      @Override
      public org.springframework.ai.chat.observation.ChatModelObservationConvention
          getIfAvailable() {
        return null;
      }

      @Override
      public org.springframework.ai.chat.observation.ChatModelObservationConvention getIfUnique() {
        return null;
      }

      @Override
      public org.springframework.ai.chat.observation.ChatModelObservationConvention getObject(
          Object... args) {
        return null;
      }
    };
  }
}
