package org.zalava.assistant.models.configuration;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.zalava.assistant.models.configuration.adapter.out.filesystem.ModelProviderStore;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;

@Configuration(proxyBeanMethods = false)
public class ModelProviderConfigurationWiring {
  @Bean
  ModelProviderConfiguration modelProviderConfiguration(
      @Value("${agent.workspace}") Resource workspace, Environment environment) throws IOException {
    return new ModelProviderConfiguration(
        new ModelProviderStore(workspace.getFilePath()), environment::getProperty);
  }
}
