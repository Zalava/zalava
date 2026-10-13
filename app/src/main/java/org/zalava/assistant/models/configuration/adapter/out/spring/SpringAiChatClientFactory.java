package org.zalava.assistant.models.configuration.adapter.out.spring;

import io.micrometer.observation.ObservationRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.mistralai.autoconfigure.MistralAiChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.zalava.assistant.conversation.adapter.out.springai.MessageChatMemoryAdvisor;

/**
 * Uses the maintained native provider auto-configurations in a separately owned non-web context.
 */
public final class SpringAiChatClientFactory {
  private final ConfigurableEnvironment environment;
  private final ChatMemory memory;
  private final ObservationRegistry observations;

  public SpringAiChatClientFactory(
      ConfigurableEnvironment environment, ChatMemory memory, ObservationRegistry observations) {
    this.environment = environment;
    this.memory = memory;
    this.observations = observations;
  }

  public LiveModelProviderRuntime.Client create(Map<String, Object> properties) {
    var context = new AnnotationConfigApplicationContext();
    context.setId("zalava-model-provider");
    context.getEnvironment().setActiveProfiles("zalava-provider-generation");
    var sources = context.getEnvironment().getPropertySources();
    for (var source : List.copyOf(sources.stream().toList())) sources.remove(source.getName());
    // Enumerable metadata prevents the binder from creating phantom nested vendor options.
    String[] sharedNames =
        environment.getPropertySources().stream()
            .filter(EnumerablePropertySource.class::isInstance)
            .map(EnumerablePropertySource.class::cast)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .filter(name -> !vendorSetting(name))
            .distinct()
            .toArray(String[]::new);
    sources.addLast(
        new EnumerablePropertySource<Object>("zalavaSharedConfiguration") {
          @Override
          public String[] getPropertyNames() {
            return sharedNames.clone();
          }

          @Override
          public Object getProperty(String name) {
            return vendorSetting(name) ? null : environment.getProperty(name);
          }
        });
    sources.addFirst(new MapPropertySource("zalavaProviderCandidate", properties));
    context.registerBean(
        ToolCallingManager.class, () -> DefaultToolCallingManager.builder().build());
    context.registerBean(ObservationRegistry.class, () -> observations);
    context.register(NativeProviderConfiguration.class);
    try {
      context.refresh();
      ChatModel model = context.getBean(ChatModel.class);
      ChatClient client =
          ChatClient.builder(model, observations, null, null, null)
              .defaultAdvisors(
                  new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(memory).build())
              .build();
      return new LiveModelProviderRuntime.Client(client, context::close);
    } catch (RuntimeException failure) {
      context.close();
      // Framework/vendor diagnostics may contain configuration; never return or retain the cause.
      throw new IllegalArgumentException(
          "Unable to apply provider configuration. Check the provider fields and credential file; your current provider is unchanged.");
    }
  }

  private static boolean vendorSetting(String name) {
    String key = name.toLowerCase(Locale.ROOT).replace('_', '.');
    return List.of(
            "spring.ai.openai.",
            "spring.ai.anthropic.",
            "spring.ai.ollama.",
            "spring.ai.google.",
            "spring.ai.mistralai.",
            "spring.ai.deepseek.",
            "spring.ai.bedrock.",
            "spring.ai.model.",
            "zalava.model.")
        .stream()
        .anyMatch(key::startsWith);
  }

  @Profile("zalava-provider-generation")
  @Configuration(proxyBeanMethods = false)
  @Import(ManagedBedrockClients.class)
  @ImportAutoConfiguration({
    OpenAiChatAutoConfiguration.class,
    AnthropicChatAutoConfiguration.class,
    OllamaApiAutoConfiguration.class,
    OllamaChatAutoConfiguration.class,
    GoogleGenAiChatAutoConfiguration.class,
    MistralAiChatAutoConfiguration.class,
    DeepSeekChatAutoConfiguration.class,
    BedrockConverseProxyChatAutoConfiguration.class
  })
  static class NativeProviderConfiguration {}
}
