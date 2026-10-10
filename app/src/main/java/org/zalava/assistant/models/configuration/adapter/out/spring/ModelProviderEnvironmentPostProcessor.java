package org.zalava.assistant.models.configuration.adapter.out.spring;

import java.io.IOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.zalava.assistant.models.configuration.adapter.out.filesystem.ModelProviderStore;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;

/** Only catalog-whitelisted model settings override launcher defaults after a save. */
public final class ModelProviderEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {
  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    try {
      var workspace =
          new DefaultResourceLoader()
              .getResource(environment.getProperty("agent.workspace", "file:./workspace/"));
      var properties =
          ModelProviderConfiguration.runtimeProperties(
              new ModelProviderStore(workspace.getFilePath()).read());
      if (!properties.isEmpty())
        environment
            .getPropertySources()
            .addFirst(new MapPropertySource("zalavaSavedModelProvider", properties));
    } catch (IOException | IllegalArgumentException exception) {
      throw new IllegalStateException("Unable to load saved model provider configuration.");
    }
  }
}
