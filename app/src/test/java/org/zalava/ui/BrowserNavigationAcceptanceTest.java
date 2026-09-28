package org.zalava.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.zalava.support.SeaComponentTestConfiguration;

/**
 * Real-browser acceptance for the product navigation shell. It proves the same navigation appears
 * on every product page including chat, only links pages the signed-in account can open, and marks
 * the active page.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  SeaComponentTestConfiguration.class,
  BrowserNavigationAcceptanceTest.AccountConfiguration.class
})
class BrowserNavigationAcceptanceTest {

  private static final Path WORKSPACE = workspace();
  private static final Path DIAGNOSTICS = diagnostics();

  private static final String ADMIN_LOGIN = "browser-nav-admin-" + UUID.randomUUID();
  private static final String ADMIN_PASSWORD = "BrowserNavAdminPassword-123";
  private static final String MEMBER_LOGIN = "browser-nav-member-" + UUID.randomUUID();
  private static final String MEMBER_PASSWORD = "BrowserNavMemberPassword-123";

  private static final List<String> ADMIN_MENU =
      List.of(
          "Dashboard",
          "Chat",
          "Jobs",
          "Knowledge",
          "Memory",
          "Monitoring",
          "Apps",
          "Modules",
          "Settings");
  private static final List<String> MEMBER_MENU =
      List.of("Dashboard", "Chat", "Jobs", "Knowledge", "Memory");
  private static final List<String> MEMBER_HIDDEN =
      List.of("/apps", "/modules", "/monitoring", "/settings");

  private static final Map<String, String> PAGES = pages();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> ADMIN_LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> ADMIN_PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add(
        "spring.allConfig.location",
        () -> WORKSPACE.resolve("private/application.private.yaml").toString());
  }

  @Test
  void administratorSeesEveryPageNavigationOnEveryPageAndChatMounts() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      startTrace(context);
      try {
        Page page = context.newPage();
        signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
        for (Map.Entry<String, String> pageEntry : PAGES.entrySet()) {
          var response = page.navigate(baseUrl() + pageEntry.getKey());
          assertThat(response.status()).isEqualTo(200);
          page.locator(".sea-navbar").waitFor();
          assertThat(menuLabels(page)).containsExactlyElementsOf(ADMIN_MENU);
          assertThat(page.locator(".sea-navbar .navbar-item.is-active").innerText().trim())
              .isEqualTo(pageEntry.getValue());
        }
        for (String link : PAGES.keySet()) {
          assertThat(page.navigate(baseUrl() + link).status()).isLessThan(400);
        }

        page.navigate(baseUrl() + "/chat");
        page.locator(".sea-navbar").waitFor();
        page.getByText("Connected").waitFor();
        assertThat(page.locator("#root").count()).isEqualTo(1);
      } finally {
        stopTrace(context);
      }
    }
  }

  @Test
  void memberOnlySeesAuthorizedNavigation() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      startTrace(context);
      try {
        Page page = context.newPage();
        signIn(page, MEMBER_LOGIN, MEMBER_PASSWORD);
        for (String path : List.of("/dashboard", "/chat", "/jobs", "/knowledge", "/memory")) {
          var response = page.navigate(baseUrl() + path);
          assertThat(response.status()).isEqualTo(200);
          page.locator(".sea-navbar").waitFor();
          assertThat(menuLabels(page)).containsExactlyElementsOf(MEMBER_MENU);
        }
        for (String hidden : MEMBER_HIDDEN) {
          assertThat(page.navigate(baseUrl() + hidden).status()).isEqualTo(403);
        }
      } finally {
        stopTrace(context);
      }
    }
  }

  private List<String> menuLabels(Page page) {
    return page.locator(".sea-navbar .navbar-start a").allTextContents();
  }

  private void startTrace(BrowserContext context) {
    context
        .tracing()
        .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
  }

  private void stopTrace(BrowserContext context) throws IOException {
    Files.createDirectories(DIAGNOSTICS);
    context
        .tracing()
        .stop(new Tracing.StopOptions().setPath(DIAGNOSTICS.resolve("navigation-trace.zip")));
  }

  private void signIn(Page page, String login, String password) {
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(login);
    page.locator("input[name=password]").fill(password);
    page.getByRole(
            com.microsoft.playwright.options.AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Sign in"))
        .click();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Map<String, String> pages() {
    Map<String, String> pages = new LinkedHashMap<>();
    pages.put("/dashboard", "Dashboard");
    pages.put("/chat", "Chat");
    pages.put("/jobs", "Jobs");
    pages.put("/knowledge", "Knowledge");
    pages.put("/memory", "Memory");
    pages.put("/monitoring", "Monitoring");
    pages.put("/apps", "Apps");
    pages.put("/modules", "Modules");
    pages.put("/settings", "Settings");
    return pages;
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("sea-browser-navigation-");
      Files.writeString(root.resolve("AGENT.md"), "Browser navigation acceptance workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static Path diagnostics() {
    try {
      return Files.createDirectories(Path.of("build", "browser-acceptance"));
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner browserNavigationAccounts(AccountLifecycle accounts) {
      return arguments -> {
        ensureActivated(accounts, ADMIN_LOGIN, ADMIN_PASSWORD, AccountRole.ADMIN);
        ensureActivated(accounts, MEMBER_LOGIN, MEMBER_PASSWORD, AccountRole.MEMBER);
      };
    }

    private static void ensureActivated(
        AccountLifecycle accounts, String login, String password, AccountRole role) {
      var account =
          accounts.findByLoginName(login).orElseGet(() -> accounts.create(login, password, role));
      if (account.passwordChangeRequired()) {
        accounts.changePassword(account.id(), password, password + "-changed");
        accounts.changePassword(account.id(), password + "-changed", password);
      }
    }
  }
}
