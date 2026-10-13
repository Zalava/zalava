package org.zalava.assistant.models.configuration;

import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.zalava.assistant.models.configuration.adapter.out.filesystem.ModelProviderStore;
import org.zalava.assistant.models.configuration.adapter.out.spring.LiveModelProviderRuntime;
import org.zalava.assistant.models.configuration.adapter.out.spring.SpringAiChatClientFactory;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;

@Configuration(proxyBeanMethods = false)
public class ModelProviderConfigurationWiring {
  @Bean
  LiveModelProviderRuntime liveModelProviderRuntime(
      ChatClient initial,
      ConfigurableEnvironment environment,
      ChatMemory memory,
      ObservationRegistry observations) {
    var factory = new SpringAiChatClientFactory(environment, memory, observations);
    return new LiveModelProviderRuntime(initial, environment::getProperty, factory::create);
  }

  @Bean
  ModelProviderConfiguration modelProviderConfiguration(
      @Value("${agent.workspace}") Resource workspace, LiveModelProviderRuntime runtime)
      throws IOException {
    return new ModelProviderConfiguration(
        new ModelProviderStore(workspace.getFilePath()), runtime::property, runtime);
  }
}
