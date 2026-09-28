package org.zalava.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
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
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Real-browser acceptance for the packaged interactive SEA conversation surface. It drives the
 * built React bundle over the real WebSocket transport and proves that only server-observed
 * execution state and pending approval details are shown, recovered from SEA after reconnect, and
 * that an owner decision persists without any client-side policy authority.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  BrowserUiInteractAcceptanceTest.AccountConfiguration.class,
  BrowserUiInteractAcceptanceTest.ModelConfiguration.class
})
class BrowserUiInteractAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final Path DIAGNOSTICS = diagnostics();
  private static final String OWNER_LOGIN = "browser-interact-owner-" + UUID.randomUUID();
  private static final String PASSWORD = "BrowserInteractPassword-123";

  @LocalServerPort private int port;
  @org.springframework.beans.factory.annotation.Autowired private AccountLifecycle accounts;
  @org.springframework.beans.factory.annotation.Autowired private ActorTaskStore tasks;
  @org.springframework.beans.factory.annotation.Autowired private SeaToolApprovalRequests approvals;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> OWNER_LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "none");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @BeforeEach
  void clearApprovals() {
    approvals.clear();
  }

  @Test
  void rendersReplayedExecutionStateAndDecidesAPendingApproval() throws IOException {
    Account owner = accounts.findByLoginName(OWNER_LOGIN).orElseThrow();
    Actor actor = new Actor(owner.id());

    ActorTaskReference completed = ActorTaskReference.newReference();
    tasks.save(
        actor, completed, task("Completed report", Task.Status.completed, "Report is ready."));
    ActorTaskReference awaiting = ActorTaskReference.newReference();
    tasks.save(
        actor,
        awaiting,
        task("Awaiting report approval", Task.Status.awaiting_human_input, "Write the report."));
    createApproval(owner, actor, awaiting);

    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      context
          .tracing()
          .start(
              new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
      try {
        Page page = context.newPage();
        signIn(page);
        page.navigate(baseUrl() + "/chat");
        page.getByText("Connected").waitFor();

        page.getByText("Execution completed").waitFor();
        page.getByText("Completed report").waitFor();
        page.getByText("Permission needed").waitFor();

        page.reload();
        page.getByText("Connected").waitFor();
        page.getByText("Permission needed").waitFor();
        assertThat(page.locator("body").innerText())
            .contains("Completed report", "Awaiting report");

        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Deny"))
            .click();
        page.getByText("Execution todo").waitFor();

        page.reload();
        page.getByText("Connected").waitFor();
        page.getByText("Execution todo").waitFor();
        String body = page.locator("body").innerText();
        assertThat(body).contains("Completed report", "Awaiting report approval");
        assertThat(body).doesNotContain("Permission needed", "Allow once");
        assertThat(tasks.get(actor, awaiting).getStatus()).isEqualTo(Task.Status.todo);
      } finally {
        context
            .tracing()
            .stop(new Tracing.StopOptions().setPath(DIAGNOSTICS.resolve("ui-interact-trace.zip")));
      }
    }
  }

  private void createApproval(Account owner, Actor actor, ActorTaskReference reference) {
    SeaProvider provider = mock(SeaProvider.class);
    ProviderDescriptor descriptor =
        new ProviderDescriptor(
            "browser-interact-provider",
            "test-module",
            "test",
            "Browser interact provider",
            "Test provider",
            "1",
            ProviderCapabilities.toolsOnly(),
            List.of("sea_backed"),
            Map.of("owner", "self"));
    SeaToolDescriptor tool = new SeaToolDescriptor("write", "Writes scoped data", true, List.of());
    when(provider.descriptor()).thenReturn(descriptor);
    when(provider.capabilities()).thenReturn(ProviderCapabilities.toolsOnly());
    when(provider.listTools()).thenReturn(List.of(tool));
    when(provider.callTool(
            org.mockito.ArgumentMatchers.eq("write"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(SeaOperationResult.success(Map.of("written", true)));
    approvals.create(
        provider,
        tool,
        new InvocationContext(
            owner.id().toString(),
            false,
            Map.of(
                "accountRole",
                "MEMBER",
                SeaToolApprovalRequests.ACTOR_TASK_REFERENCE,
                new ActorTaskExecutionReference(actor, reference).encode())),
        JsonNodeFactory.instance.objectNode());
  }

  private void signIn(Page page) {
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(OWNER_LOGIN);
    page.locator("input[name=password]").fill(PASSWORD);
    page.getByRole(
            com.microsoft.playwright.options.AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Sign in"))
        .click();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Task task(String name, Task.Status status, String description) {
    return new Task(null, name, Instant.now(), status, description);
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("sea-browser-interact-");
      Files.writeString(root.resolve("AGENT.md"), "Browser interact acceptance workspace.");
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
    ApplicationRunner browserInteractAccounts(AccountLifecycle accounts) {
      return arguments -> {
        if (accounts.findByLoginName(OWNER_LOGIN).isPresent()) {
          return;
        }
        Account account = accounts.create(OWNER_LOGIN, PASSWORD, AccountRole.MEMBER);
        accounts.changePassword(account.id(), PASSWORD, PASSWORD + "-changed");
        accounts.changePassword(account.id(), PASSWORD + "-changed", PASSWORD);
      };
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ModelConfiguration {
    @Bean
    ChatModel browserInteractChatModel() {
      return mock(ChatModel.class);
    }
  }
}
