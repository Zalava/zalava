package org.zalava.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;

/** Exercises the secured first-run path with real form sessions and persisted workspace state. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ChatControllerComponentTest.ChatModelTestConfiguration.class,
  BrowserOnboardingAcceptanceTest.AccountConfiguration.class
})
class BrowserOnboardingAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String LOGIN = "onboarding-browser-" + UUID.randomUUID();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add(
        "spring.allConfig.location",
        () -> WORKSPACE.resolve("private/application.private.yaml").toString());
    registry.add("agent.onboarding.completed", () -> "false");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> "OnboardingTestPassword-123");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void replacesBootstrapPasswordRejectsInvalidCredentialsAndPersistsOnboarding()
      throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/login");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill("wrong-password");
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Sign in"))
          .click();
      assertThat(page.url()).contains("/login?error");

      page.navigate(baseUrl() + "/login");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill("OnboardingTestPassword-123");
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Sign in"))
          .click();
      assertThat(page.url()).endsWith("/account/password");
      page.locator("input[name=currentPassword]").fill("OnboardingTestPassword-123");
      page.locator("input[name=replacementPassword]").fill("OnboardingTestPassword-456");
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Change password"))
          .click();

      page.navigate(baseUrl() + "/onboarding/provider");
      page.locator("input[value=anthropic]").check();
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Continue"))
          .click();
      page.locator("input[name=apiKey]").fill("browser-onboarding-key");
      page.locator("input[name=model]").fill("claude-sonnet-4-6");
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Continue"))
          .click();
      page.locator("textarea[name=agentContent]").fill("# Browser onboarding instructions");
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Continue"))
          .click();
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Continue"))
          .click();
      page.getByText("configured.").waitFor();
    }

    assertThat(WORKSPACE.resolve("AGENT.private.md"))
        .hasContent("# Browser onboarding instructions");
    assertThat(WORKSPACE.resolve("private/application.private.yaml"))
        .content()
        .contains("anthropic")
        .contains("browser-onboarding-key")
        .contains("onboarding");
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("sea-browser-onboarding-");
      Files.writeString(root.resolve("AGENT.md"), "Browser onboarding test workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner onboardingBrowserAccount(AccountLifecycle accounts) {
      return arguments ->
          accounts
              .findByLoginName(LOGIN)
              .orElseGet(
                  () -> accounts.create(LOGIN, "OnboardingTestPassword-123", AccountRole.ADMIN));
    }
  }
}
