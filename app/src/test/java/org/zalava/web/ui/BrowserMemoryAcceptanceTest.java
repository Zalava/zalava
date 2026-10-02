package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
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
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;

/**
 * Real-browser acceptance for the product Memory screen: an administrator proposes a durable
 * memory, approves it, inspects it, edits it, sees the edit persist across a reload, and deletes
 * it, all through the real server-rendered product shell and the real filesystem memory store.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(BrowserMemoryAcceptanceTest.AccountConfiguration.class)
class BrowserMemoryAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String LOGIN = "memory-management-" + UUID.randomUUID();
  private static final String PASSWORD = "MemoryManagementPassword-123";

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void proposesApprovesEditsAndDeletesAMemory() {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page);

      page.navigate(baseUrl() + "/memory");
      page.locator("#proposal-text").fill("browser durable fact");
      page.locator("[data-action=propose]").click();
      page.locator("[data-memory-proposal]").waitFor();
      assertThat(page.locator("[data-memory-proposal]").first().innerText())
          .contains("browser durable fact");

      page.locator("[data-action=approve]").first().click();
      page.locator("[data-memory-entry]").waitFor();
      assertThat(page.locator("[data-memory-entry]").first().innerText())
          .contains("browser durable fact");

      page.locator("[data-memory-entry] a").first().click();
      page.locator("[data-memory-detail]").waitFor();
      page.locator("#memory-text").fill("browser edited fact");
      page.locator("[data-action=save]").click();
      page.getByText("browser edited fact").first().waitFor();

      page.reload();
      assertThat(page.locator("body").innerText()).contains("browser edited fact");
      assertThat(page.locator("[data-memory-updated]").count()).isEqualTo(1);

      page.navigate(baseUrl() + "/memory");
      page.locator("[data-memory-entry] [data-action=delete]").first().click();
      page.locator("[data-memory-entry]")
          .waitFor(
              new com.microsoft.playwright.Locator.WaitForOptions()
                  .setState(WaitForSelectorState.DETACHED));

      page.reload();
      assertThat(page.locator("[data-memory-entry]").count()).isZero();
      assertThat(page.locator("body").innerText()).doesNotContain("browser edited fact");
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
      Path root = Files.createTempDirectory("sea-browser-memory-");
      Files.writeString(root.resolve("AGENT.md"), "Browser memory workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner browserMemoryAccount(AccountLifecycle accounts) {
      return arguments ->
          accounts
              .findByLoginName(LOGIN)
              .orElseGet(() -> accounts.create(LOGIN, PASSWORD, AccountRole.ADMIN));
    }
  }
}
