package org.zalava.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class ZalavaComponentTestInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Path WORKSPACE = createWorkspace();

  public static Path workspacePath() {
    return WORKSPACE;
  }

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    initialize(context, false, false);
  }

  static void initialize(ConfigurableApplicationContext context, boolean securityEnabled) {
    initialize(context, securityEnabled, false);
  }

  static void initialize(
      ConfigurableApplicationContext context, boolean securityEnabled, boolean providerEnabled) {
    TestPropertyValues.of(
            "agent.workspace=" + WORKSPACE.toUri(),
            "agent.onboarding.completed=true",
            "agent.channels.telegram.token=false",
            "agent.channels.telegram.username=false",
            "spring.ai.model.chat="
                + context.getEnvironment().getProperty("zalava.test.chat-provider", "unknown"),
            "spring.ai.openai.api-key=component-test-not-a-real-key",
            "spring.datasource.url=" + PostgreSqlTestDatabase.newJdbcUrl(),
            "jobrunr.background-job-server.enabled=false",
            "jobrunr.dashboard.enabled=false",
            "zalava.accounts.security-enabled=" + securityEnabled,
            "agent.browser.brave.api-key=" + (providerEnabled ? "test-key" : ""),
            "agent.tools.playwright.enabled=" + providerEnabled,
            "agent.modules.local-artifact-roots=" + (providerEnabled ? WORKSPACE : ""),
            "zalava.release.image=zalava-local:component",
            "zalava.release.revision=component")
        .applyTo(context.getEnvironment());
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("zalava-component-workspace-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
      Files.writeString(
          skill.resolve("SKILL.md"),
          """
                    ---
                    name: test-skill
                    description: Minimal component test skill.
                    ---
                    # Test Skill
                    """);
      Path adminSkill = Files.createDirectories(workspace.resolve("skills/admin-skill"));
      Files.writeString(
          adminSkill.resolve("SKILL.md"),
          """
                    ---
                    name: admin-skill
                    description: Administrator-only component test skill.
                    visibility: admin
                    ---
                    # Admin Skill
                    """);
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
