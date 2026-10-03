package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
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
import org.zalava.api.InvocationContext;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import tools.jackson.databind.node.JsonNodeFactory;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  JobDetailControllerComponentTest.RuntimeTestConfiguration.class,
  BrowserJobsAcceptanceTest.AccountConfiguration.class,
  BrowserJobsAcceptanceTest.ModelConfiguration.class
})
class BrowserJobsAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String OWNER_LOGIN = "browser-job-owner-" + UUID.randomUUID();
  private static final String OTHER_LOGIN = "browser-job-other-" + UUID.randomUUID();
  private static final String PASSWORD = "BrowserJobPassword-123";

  @LocalServerPort private int port;
  @org.springframework.beans.factory.annotation.Autowired private AccountLifecycle accounts;
  @org.springframework.beans.factory.annotation.Autowired private ActorTaskStore tasks;

  @org.springframework.beans.factory.annotation.Autowired
  private ZalavaToolApprovalRequests approvals;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> OWNER_LOGIN);
    registry.add("zalava.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "none");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @org.springframework.beans.factory.annotation.Autowired
  private org.zalava.tasks.application.port.in.ActorTaskCommands actorTasks;

  @org.springframework.beans.factory.annotation.Autowired
  private org.jobrunr.storage.StorageProvider storageProvider;

  @BeforeEach
  void clearApprovals() {
    approvals.clear();
  }

  @Test
  void limitsJobsToTheirActorAndKeepsDeniedApprovalVisibleAfterReload() throws IOException {
    Account owner = account(OWNER_LOGIN);
    Account other = account(OTHER_LOGIN);
    Actor ownerActor = new Actor(owner.id());
    ActorTaskReference ownerReference = ActorTaskReference.newReference();
    tasks.save(
        ownerActor, ownerReference, task("Owner approval job", Task.Status.awaiting_human_input));
    createApproval(owner, ownerActor, ownerReference);
    ActorTaskReference otherReference = ActorTaskReference.newReference();
    tasks.save(
        new Actor(other.id()),
        otherReference,
        task("Other actor job", Task.Status.awaiting_human_input));

    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext ownerContext = browser.newContext();
        BrowserContext otherContext = browser.newContext()) {
      Page ownerPage = ownerContext.newPage();
      signIn(ownerPage, OWNER_LOGIN);
      ownerPage.navigate(baseUrl() + "/jobs");
      ownerPage.getByText("Owner approval job").waitFor();
      assertThat(ownerPage.locator("body").innerText()).doesNotContain("Other actor job");

      Page otherPage = otherContext.newPage();
      signIn(otherPage, OTHER_LOGIN);
      assertThat(otherPage.navigate(baseUrl() + "/jobs/" + ownerReference.value()).status())
          .isEqualTo(404);

      ownerPage.getByText("Owner approval job").click();
      ownerPage
          .getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Deny and continue"))
          .click();
      ownerPage.getByText("Denied").waitFor();
      ownerPage.reload();
      ownerPage.getByText("Denied").waitFor();
      assertThat(ownerPage.locator("body").innerText()).contains("Owner approval job");
      assertThat(
              ownerPage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON).allInnerTexts())
          .doesNotContain("Deny and continue");
    }
  }

  @Test
  void readsSavedReportsAndRealScheduleChangesWithoutCrossAccountLeakage() throws Exception {
    String login = "browser-job-evidence-" + UUID.randomUUID();
    AccountConfiguration.createAccount(accounts, login);
    Account owner = account(login);
    Actor actor = new Actor(owner.id());
    var report = ActorTaskReference.newReference();
    String output = "# Browser saved report\nActual persisted output.";
    tasks.save(
        actor, report, task("Saved browser report", Task.Status.completed).withFeedback(output));
    var due = java.time.LocalDateTime.of(2050, 1, 2, 12, 0);
    var upcoming = actorTasks.schedule(actor, due, "Future browser job", "Scheduled fixture");
    var actual =
        new org.zalava.tasks.adapter.out.jobrunr.JobRunrActorTaskSchedules(storageProvider)
            .list(actor).stream()
                .filter(schedule -> schedule.reference().equals(upcoming))
                .findFirst()
                .orElseThrow();
    var other = account(OTHER_LOGIN);
    tasks.save(
        new Actor(other.id()),
        ActorTaskReference.newReference(),
        task("Foreign browser report", Task.Status.completed).withFeedback("Other account output"));
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Path diagnostics =
          Files.createDirectories(Path.of("build", "browser-acceptance", "job-evidence"));
      context
          .tracing()
          .start(
              new com.microsoft.playwright.Tracing.StartOptions()
                  .setScreenshots(true)
                  .setSnapshots(true)
                  .setSources(true));
      Page page = context.newPage();
      try {
        signIn(page, login);
        page.navigate(baseUrl() + "/jobs");
        var reports = page.locator("[aria-labelledby='saved-reports-title']");
        reports.getByText("Saved browser report").waitFor();
        assertThat(page.locator("body").innerText())
            .doesNotContain("Foreign browser report", "Other account output");
        var download =
            page.waitForDownload(
                () ->
                    reports
                        .getByRole(
                            com.microsoft.playwright.options.AriaRole.LINK,
                            new com.microsoft.playwright.Locator.GetByRoleOptions()
                                .setName("Download saved report"))
                        .click());
        assertThat(download.suggestedFilename()).isEqualTo("job-report-" + report.value() + ".md");
        assertThat(Files.readString(download.path())).isEqualTo(output);
        reports
            .getByRole(
                com.microsoft.playwright.options.AriaRole.LINK,
                new com.microsoft.playwright.Locator.GetByRoleOptions()
                    .setName("Saved browser report"))
            .focus();
        page.keyboard().press("Enter");
        page.waitForURL(baseUrl() + "/jobs/" + report.value());
        assertThat(page.locator("#artifacts").innerText()).contains("Saved job report");
        page.navigate(baseUrl() + "/dashboard");
        page.locator("[aria-labelledby='upcoming-jobs-title']")
            .getByText("Future browser job")
            .waitFor();
        var changed = java.time.Instant.parse("2050-01-03T14:00:00Z");
        var job = storageProvider.getJobById(actual.scheduleId());
        job.scheduleAt(changed, "Browser fixture change");
        storageProvider.save(job);
        page.reload();
        page.locator("time[datetime='" + changed + "']").waitFor();
        for (int width : new int[] {375, 768, 1440}) {
          page.setViewportSize(width, 900);
          assertThat(page.locator("[aria-labelledby='saved-reports-title']").isVisible()).isTrue();
          assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
              .isEqualTo(true);
        }
        var cancelled = storageProvider.getJobById(actual.scheduleId());
        cancelled.delete("Browser fixture cancellation");
        storageProvider.save(cancelled);
        page.reload();
        page.getByText("No upcoming scheduled jobs.").waitFor();
        try (BrowserContext foreign = browser.newContext()) {
          Page otherPage = foreign.newPage();
          signIn(otherPage, OTHER_LOGIN);
          assertThat(
                  foreign
                      .request()
                      .get(baseUrl() + "/jobs/" + report.value() + "/artifacts/report")
                      .status())
              .isEqualTo(404);
        }
      } catch (Throwable failure) {
        page.screenshot(new Page.ScreenshotOptions().setPath(diagnostics.resolve("failure.png")));
        Files.writeString(diagnostics.resolve("failure.html"), page.content());
        throw failure;
      } finally {
        context
            .tracing()
            .stop(
                new com.microsoft.playwright.Tracing.StopOptions()
                    .setPath(diagnostics.resolve("job-evidence-trace.zip")));
      }
    }
  }

  private Account account(String login) {
    return accounts.findByLoginName(login).orElseThrow();
  }

  private void createApproval(Account owner, Actor actor, ActorTaskReference reference) {
    ZalavaProvider provider = mock(ZalavaProvider.class);
    ProviderDescriptor descriptor =
        new ProviderDescriptor(
            "browser-scoped-provider",
            "test-module",
            "test",
            "Browser scoped provider",
            "Test provider",
            "1",
            ProviderCapabilities.toolsOnly(),
            List.of("zalava_backed"),
            Map.of("owner", "self"));
    ZalavaToolDescriptor tool =
        new ZalavaToolDescriptor("write", "Writes scoped data", true, List.of());
    when(provider.descriptor()).thenReturn(descriptor);
    when(provider.capabilities()).thenReturn(ProviderCapabilities.toolsOnly());
    when(provider.listTools()).thenReturn(List.of(tool));
    when(provider.callTool(
            org.mockito.ArgumentMatchers.eq("write"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(ZalavaOperationResult.success(Map.of("written", true)));
    approvals.create(
        provider,
        tool,
        new InvocationContext(
            owner.id().toString(),
            false,
            Map.of(
                "accountRole",
                "MEMBER",
                ZalavaToolApprovalRequests.ACTOR_TASK_REFERENCE,
                new ActorTaskExecutionReference(actor, reference).encode())),
        JsonNodeFactory.instance.objectNode());
  }

  private void signIn(Page page, String login) {
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(login);
    page.locator("input[name=password]").fill(PASSWORD);
    page.getByRole(
            com.microsoft.playwright.options.AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Sign in"))
        .click();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Task task(String name, Task.Status status) {
    return new Task(null, name, Instant.now(), status, "Browser jobs acceptance task.");
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("zalava-browser-jobs-");
      Files.writeString(root.resolve("AGENT.md"), "Browser jobs acceptance workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner browserJobAccounts(AccountLifecycle accounts) {
      return arguments -> {
        createAccount(accounts, OWNER_LOGIN);
        createAccount(accounts, OTHER_LOGIN);
      };
    }

    private static void createAccount(AccountLifecycle accounts, String login) {
      if (accounts.findByLoginName(login).isPresent()) {
        return;
      }
      Account account = accounts.create(login, PASSWORD, AccountRole.MEMBER);
      accounts.changePassword(account.id(), PASSWORD, PASSWORD + "-changed");
      accounts.changePassword(account.id(), PASSWORD + "-changed", PASSWORD);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ModelConfiguration {
    @Bean
    ChatModel browserJobsChatModel() {
      return mock(ChatModel.class);
    }
  }
}
