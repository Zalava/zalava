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
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.zalava.agent.application.port.out.AgentRunStore;
import org.zalava.agent.domain.AgentRun;
import org.zalava.support.SeaComponentTestConfiguration;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.Task;

/**
 * Real-browser acceptance for the administrator Monitoring screen. It proves live work renders, a
 * freshly recorded run appears after a reload (freshness/reconnect, no stale view) and the run
 * links to its owning job.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  SeaComponentTestConfiguration.class,
  BrowserMonitoringAcceptanceTest.AccountConfiguration.class
})
class BrowserMonitoringAcceptanceTest {

  private static final Path WORKSPACE = workspace();
  private static final Path DIAGNOSTICS = diagnostics();
  private static final Path APPLICATION_LOG = WORKSPACE.resolve("sea-browser.log");

  private static final String ADMIN_LOGIN = "browser-monitoring-admin-" + UUID.randomUUID();
  private static final String ADMIN_PASSWORD = "BrowserMonitoringPassword-123";

  @LocalServerPort private int port;

  @Autowired private AccountLifecycle accounts;
  @Autowired private AgentRunStore runStore;
  @Autowired private TaskStore taskStore;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("logging.file.name", APPLICATION_LOG::toString);
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> ADMIN_LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> ADMIN_PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void administratorSeesLiveWorkAndRecoveredRunEvidenceAfterReload() throws IOException {
    taskStore.save(
        new Task(
            null,
            "Monitoring live job",
            Instant.now(),
            Task.Status.in_progress,
            "Browser monitoring acceptance task."));

    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      startTrace(context);
      try {
        Page page = context.newPage();
        signIn(page, ADMIN_LOGIN);

        var response = page.navigate(baseUrl() + "/monitoring");
        assertThat(response.status()).isEqualTo(200);
        page.locator("[data-monitoring-snapshot]").waitFor();
        assertThat(page.locator(".sea-navbar .navbar-item.is-active").innerText().trim())
            .isEqualTo("Monitoring");
        assertThat(page.locator("body").innerText()).contains("Monitoring live job");
        assertThat(page.locator("[data-empty=\"runs\"]").count()).isEqualTo(1);

        String taskId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();
        runStore.record(run("browser-monitoring-run", accountId + ":" + taskId));

        page.reload();
        page.locator("[data-run-id=\"browser-monitoring-run\"]").waitFor();
        assertThat(page.locator("[data-empty=\"runs\"]").count()).isEqualTo(0);
        assertThat(page.locator("[data-run-evidence]").getAttribute("href"))
            .isEqualTo("/jobs/" + taskId);

        Files.writeString(
            APPLICATION_LOG,
            "browser log before restart\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.LINK,
                new Page.GetByRoleOptions().setName("View Zalava logs"))
            .click();
        assertThat(page.url()).endsWith("/monitoring/logs");
        assertThat(page.locator("body").innerText()).contains("browser log before restart");
      } finally {
        stopTrace(context);
      }
    }
  }

  private static AgentRun run(String id, String conversationId) {
    Instant startedAt = Instant.now();
    return new AgentRun(
        id,
        conversationId,
        AgentRun.PromptType.CONVERSATIONAL,
        "browser monitoring prompt",
        0,
        0,
        0,
        0,
        List.of(),
        startedAt,
        startedAt.plusMillis(25),
        25,
        AgentRun.Status.SUCCEEDED,
        "browser monitoring result",
        null);
  }

  private void signIn(Page page, String login) {
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(login);
    page.locator("input[name=password]").fill(ADMIN_PASSWORD);
    page.getByRole(
            com.microsoft.playwright.options.AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Sign in"))
        .click();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
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
        .stop(new Tracing.StopOptions().setPath(DIAGNOSTICS.resolve("monitoring-trace.zip")));
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("sea-browser-monitoring-");
      Files.writeString(root.resolve("AGENT.md"), "Browser monitoring acceptance workspace.");
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
    ApplicationRunner browserMonitoringAccounts(AccountLifecycle accounts) {
      return arguments -> ensureActivated(accounts, ADMIN_LOGIN, ADMIN_PASSWORD, AccountRole.ADMIN);
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
