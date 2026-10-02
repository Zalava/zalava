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
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
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
          .isEqualTo(403);

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
            List.of("sea_backed"),
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
                SeaToolApprovalRequests.ACTOR_TASK_REFERENCE,
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
      Path root = Files.createTempDirectory("sea-browser-jobs-");
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
