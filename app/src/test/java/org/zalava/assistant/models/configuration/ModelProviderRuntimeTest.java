package org.zalava.assistant.models.configuration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.mistralai.autoconfigure.MistralAiChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.zalava.assistant.models.configuration.adapter.out.filesystem.ModelProviderStore;
import org.zalava.assistant.models.configuration.adapter.out.spring.ModelProviderEnvironmentPostProcessor;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog;

/**
 * Initializes actual packaged Spring AI clients from saved settings, never injected model mocks.
 */
class ModelProviderRuntimeTest {
  @TempDir Path root;

  ApplicationContextRunner runner(Path workspace) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                OpenAiChatAutoConfiguration.class,
                AnthropicChatAutoConfiguration.class,
                OllamaApiAutoConfiguration.class,
                OllamaChatAutoConfiguration.class,
                GoogleGenAiChatAutoConfiguration.class,
                MistralAiChatAutoConfiguration.class,
                DeepSeekChatAutoConfiguration.class,
                BedrockConverseProxyChatAutoConfiguration.class))
        .withBean(ToolCallingManager.class, () -> DefaultToolCallingManager.builder().build())
        .withSystemProperties("aws.region=us-east-1", "aws.disableEc2Metadata=true")
        .withPropertyValues("spring.ai.model.chat=unknown", "agent.workspace=" + workspace.toUri())
        .withInitializer(
            context ->
                new ModelProviderEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), new SpringApplication()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "openai",
        "anthropic",
        "ollama",
        "google-genai",
        "mistral",
        "deepseek",
        "bedrock-converse",
        "google-vertex",
        "microsoft-foundry",
        "github-models",
        "groq",
        "nvidia",
        "perplexity",
        "minimax",
        "openai-compatible"
      })
  void restartLoadsExactlyTheSelectedNativeChatIntegration(String id) throws Exception {
    Path workspace = root.resolve("workspace");
    var values = ModelProviderConfigurationTest.valid(ChatProviderCatalog.require(id));
    if (id.equals("bedrock-converse")) {
      values.put("accessKey", "fixture-key");
      values.put("secretKey", "fixture-secret");
    }
    if (id.equals("google-vertex")) {
      Path credentials = root.resolve("fixture-google.json");
      Files.writeString(
          credentials,
          "{\"type\":\"authorized_user\",\"client_id\":\"fixture\",\"client_secret\":\"fixture\",\"refresh_token\":\"fixture\"}");
      values.put("credentialsUri", credentials.toUri().toString());
    }
    var environment = new MockEnvironment();
    new ModelProviderConfiguration(new ModelProviderStore(workspace), environment::getProperty)
        .save(id, values);
    runner(workspace)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class);
              assertThat(context.getEnvironment().getProperty("spring.ai.model.chat"))
                  .isEqualTo(ChatProviderCatalog.require(id).runtimeId());
              assertThat(context.getBean(ChatModel.class).getOptions().getModel())
                  .isEqualTo("test-model");
            });
  }

  @Test
  void selectedOpenAiCompatibleEndpointReceivesTheSavedModelAndReturnsARealResponse()
      throws Exception {
    WireMockServer server = new WireMockServer(0);
    server.start();
    try {
      server.stubFor(
          post(urlEqualTo("/v1/chat/completions"))
              .willReturn(
                  okJson(
                      """
          {"id":"fixture","object":"chat.completion","created":1,"model":"fixture-model",
           "choices":[{"index":0,"message":{"role":"assistant","content":"Fixture response"},"finish_reason":"stop"}]}
          """)));
      Path workspace = root.resolve("workspace");
      var environment = new MockEnvironment();
      new ModelProviderConfiguration(new ModelProviderStore(workspace), environment::getProperty)
          .save(
              "openai-compatible",
              Map.of(
                  "model",
                  "fixture-model",
                  "apiKey",
                  "fixture-key",
                  "baseUrl",
                  server.baseUrl() + "/v1"));
      runner(workspace)
          .run(
              context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class);
                assertThat(context.getBean(OpenAiCommonProperties.class).getApiKey())
                    .isEqualTo("fixture-key");
                assertThat(context.getBean(OpenAiChatProperties.class).getApiKey()).isNull();
                assertThat(context.getBean(ChatModel.class).call("Hello fixture"))
                    .isEqualTo("Fixture response");
              });
      server.verify(
          postRequestedFor(urlEqualTo("/v1/chat/completions"))
              .withRequestBody(matchingJsonPath("$.model", equalTo("fixture-model")))
              .withHeader("Authorization", equalTo("Bearer fixture-key")));
    } finally {
      server.stop();
    }
  }
}
