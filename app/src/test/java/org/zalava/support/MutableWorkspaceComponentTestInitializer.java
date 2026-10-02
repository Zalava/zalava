package org.zalava.support;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

final class MutableWorkspaceComponentTestInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override
  public void initialize(ConfigurableApplicationContext context) {
    SeaComponentTestInitializer.initialize(context, false);
    Path workspace =
        Path.of(URI.create(context.getEnvironment().getRequiredProperty("agent.workspace")));
    TestPropertyValues.of(
            "spring.ai.model.chat=openai",
            "spring.allConfig.location=" + workspace.resolve("private/application.private.yaml"))
        .applyTo(context.getEnvironment());
  }
}
