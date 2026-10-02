package org.zalava.web.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.api.InvocationContext;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import tools.jackson.databind.node.JsonNodeFactory;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(AuthenticatedMockMvcTestConfiguration.class)
@WithMockUser(username = "dashboard-admin", roles = "ADMIN")
class DashboardControllerComponentTest {

  private static final Path WORKSPACE = createWorkspace();
  private static final AtomicInteger LOGINS = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountLifecycle accounts;
  @Autowired private ActorTaskStore tasks;
  @Autowired private TaskStore legacyTasks;
  @Autowired private SeaToolApprovalRequests approvals;

  @BeforeEach
  void enableBootstrapAdministrator() {
    Account administrator =
        accounts
            .findByLoginName("dashboard-admin")
            .orElseGet(
                () ->
                    accounts.create(
                        "dashboard-admin",
                        "TestBootstrapPassword-123",
                        org.zalava.identity.accounts.domain.AccountRole.ADMIN));
    if (administrator.passwordChangeRequired()) {
      accounts.changePassword(
          administrator.id(), "TestBootstrapPassword-123", "AdministratorPassword-123");
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("sea.accounts.bootstrap-login", () -> "dashboard-admin");
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void rendersLegacyDashboardWithCountsAndCurrentWork() throws Exception {
    legacyTasks.save(task("Running legacy job", Instant.now(), Task.Status.in_progress));
    legacyTasks.save(task("Awaiting legacy job", Instant.now(), Task.Status.awaiting_human_input));
    legacyTasks.save(task("Completed legacy job", Instant.now(), Task.Status.completed));

    mockMvc
        .perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Dashboard</title>")))
        .andExpect(content().string(containsString("data-metric=\"running-jobs\">0<")))
        .andExpect(content().string(not(containsString("Running legacy job"))));
  }

  @Test
  void rendersActorScopedDashboardWithPendingApprovalsAndCurrentWork() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference running = ActorTaskReference.newReference();
    ActorTaskReference awaiting = ActorTaskReference.newReference();
    tasks.save(actor, running, task("My running job", Instant.now(), Task.Status.in_progress));
    tasks.save(
        actor, awaiting, task("My awaiting job", Instant.now(), Task.Status.awaiting_human_input));
    ActorTaskExecutionReference execution = new ActorTaskExecutionReference(actor, awaiting);
    approvals.create(
        scopedProvider(),
        new ZalavaToolDescriptor("write", "Writes scoped data", true, List.of("member-safe")),
        new InvocationContext(
            owner.id().toString(),
            false,
            Map.of(
                "accountRole",
                "MEMBER",
                SeaToolApprovalRequests.ACTOR_TASK_REFERENCE,
                execution.encode())),
        JsonNodeFactory.instance.objectNode());

    mockMvc
        .perform(get("/dashboard").with(user(owner.loginName())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("My running job")))
        .andExpect(content().string(containsString("My awaiting job")))
        .andExpect(content().string(containsString("data-metric=\"pending-approvals\">1<")))
        .andExpect(content().string(containsString("/jobs/" + awaiting.value())));

    Account other = member();
    Actor otherActor = new Actor(other.id());
    ActorTaskReference otherReference = ActorTaskReference.newReference();
    tasks.save(
        otherActor,
        otherReference,
        task("Other private job", Instant.now(), Task.Status.in_progress));

    mockMvc
        .perform(get("/dashboard").with(user(owner.loginName())))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Other private job"))));
  }

  private Account member() {
    String login = "dashboard-member-" + LOGINS.incrementAndGet();
    Account account = accounts.create(login, "TemporaryPassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "TemporaryPassword-123", "PermanentPassword-123");
    return accounts.findByLoginName(login).orElseThrow();
  }

  private static Task task(String name, Instant createdAt, Task.Status status) {
    return new Task(null, name, createdAt, status, "Dashboard component test task.");
  }

  private static ZalavaProvider scopedProvider() {
    ZalavaProvider provider = mock(ZalavaProvider.class);
    ProviderDescriptor descriptor =
        new ProviderDescriptor(
            "scoped-provider",
            "test-module",
            "test",
            "Scoped Provider",
            "Test provider",
            "1",
            ProviderCapabilities.toolsOnly(),
            List.of("sea_backed"),
            Map.of("owner", "self"));
    when(provider.descriptor()).thenReturn(descriptor);
    when(provider.capabilities()).thenReturn(ProviderCapabilities.toolsOnly());
    when(provider.listTools())
        .thenReturn(
            List.of(
                new ZalavaToolDescriptor(
                    "write", "Writes scoped data", true, List.of("member-safe"))));
    return provider;
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("dashboard-component-test-");
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
      return workspace;
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }
}
