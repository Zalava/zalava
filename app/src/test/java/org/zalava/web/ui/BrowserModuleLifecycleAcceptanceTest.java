package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
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
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.ZalavaComponentTestConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ZalavaComponentTestConfiguration.class,
  ModuleLifecycleComponentTest.FixtureConfiguration.class,
  BrowserModuleLifecycleAcceptanceTest.AccountConfiguration.class
})
class BrowserModuleLifecycleAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String LOGIN = "module-lifecycle-" + UUID.randomUUID();
  private static final String PASSWORD = "ModuleLifecyclePassword-123";

  @LocalServerPort int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("zalava.module-configuration.root", WORKSPACE::toString);
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> LOGIN);
    registry.add("zalava.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void configureStartChangeAndStopWithoutRestartingSea() {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page);
      page.navigate(baseUrl() + "/modules/lifecycle-fixture");
      assertThat(page.locator("[data-runtime-state]").getAttribute("data-runtime-state"))
          .isEqualTo("SETUP_REQUIRED");

      page.locator("input[name='configuration.fixture.endpoint']").fill("ready");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save configuration"))
          .click();
      page.getByText("Configuration applied. Start the module when ready.").waitFor();
      page.navigate(baseUrl() + "/modules/lifecycle-fixture");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start module")).click();
      assertThat(page.locator("[data-runtime-state]").getAttribute("data-runtime-state"))
          .isEqualTo("RUNNING");

      page.locator("input[name='configuration.fixture.endpoint']").fill("broken");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save configuration"))
          .click();
      page.getByText(
              "Module configuration could not be activated. Previous settings remain active.")
          .waitFor();
      page.navigate(baseUrl() + "/modules/lifecycle-fixture");
      assertThat(page.locator("[data-runtime-state]").getAttribute("data-runtime-state"))
          .isEqualTo("RUNNING");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Stop module")).click();
      assertThat(page.locator("[data-runtime-state]").getAttribute("data-runtime-state"))
          .isEqualTo("STOPPED");
    }
  }

  private void signIn(Page page) {
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(LOGIN);
    page.locator("input[name=password]").fill(PASSWORD);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    page.locator("input[name=currentPassword]").fill(PASSWORD);
    page.locator("input[name=replacementPassword]").fill(PASSWORD + "-changed");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password")).click();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Path workspace() {
    try {
      return Files.createTempDirectory("zalava-module-lifecycle-browser-");
    } catch (java.io.IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner lifecycleBrowserAccount(AccountLifecycle accounts) {
      return arguments ->
          accounts
              .findByLoginName(LOGIN)
              .orElseGet(() -> accounts.create(LOGIN, PASSWORD, AccountRole.ADMIN));
    }
  }
}
