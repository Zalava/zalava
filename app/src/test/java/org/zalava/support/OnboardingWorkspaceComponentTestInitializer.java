package org.zalava.support;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

final class OnboardingWorkspaceComponentTestInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    ZalavaComponentTestInitializer.initialize(context, false);
    Path workspace =
        Path.of(URI.create(context.getEnvironment().getRequiredProperty("agent.workspace")));
    TestPropertyValues.of(
            "agent.workspace=" + workspace.toUri() + "/",
            "agent.onboarding.completed=false",
            "spring.allConfig.location=" + workspace.resolve("private/application.private.yaml"))
        .applyTo(context.getEnvironment());
  }
}
