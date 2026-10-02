package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.api.InvocationContext;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.runtime.LoadedSeaProvider;
import org.zalava.modules.runtime.SeaRuntime;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import tools.jackson.databind.node.JsonNodeFactory;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  JobDetailControllerComponentTest.RuntimeTestConfiguration.class,
  AuthenticatedMockMvcTestConfiguration.class
})
@WithMockUser(username = "job-detail-admin", roles = "ADMIN")
class JobDetailControllerComponentTest {

  private static final Path WORKSPACE = createWorkspace();
  private static final AtomicInteger LOGINS = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountLifecycle accounts;
  @Autowired private ActorTaskStore tasks;
  @Autowired private TaskStore legacyTasks;
  @Autowired private SeaToolApprovalRequests approvals;
  @Autowired private SeaRuntime seaRuntime;

  @BeforeEach
  void enableBootstrapAdministrator() {
    Account administrator =
        accounts
            .findByLoginName("job-detail-admin")
            .orElseGet(
                () ->
                    accounts.create(
                        "job-detail-admin",
                        "TestBootstrapPassword-123",
                        org.zalava.identity.accounts.domain.AccountRole.ADMIN));
    if (administrator.passwordChangeRequired()) {
      accounts.changePassword(
          administrator.id(), "TestBootstrapPassword-123", "AdministratorPassword-123");
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("sea.accounts.bootstrap-login", () -> "job-detail-admin");
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void rendersActorJobDetailForTheOwningMember() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Private actor job", Task.Status.completed));

    mockMvc
        .perform(get("/jobs/" + reference.value()).with(user(owner.loginName())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Zalava Job")))
        .andExpect(content().string(containsString("Private actor job")))
        .andExpect(content().string(containsString("Completed")));
  }

  @Test
  void actorJobDetailReturnsNotFoundForUnknownReference() throws Exception {
    Account owner = member();
    mockMvc
        .perform(
            get("/jobs/" + ActorTaskReference.newReference().value()).with(user(owner.loginName())))
        .andExpect(status().isNotFound());
  }

  @Test
  void rendersLegacyJobDetailForAnonymousOperators() throws Exception {
    Task task = legacyTasks.save(task("Legacy public job", Task.Status.completed));
    String url = legacyJobUrl(task);

    mockMvc
        .perform(get(url))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Legacy public job")));
  }

  @Test
  void legacyJobDetailRejectsInvalidReferencesAndMissingTasks() throws Exception {
    mockMvc.perform(get("/jobs/not-a-date/file.md")).andExpect(status().isNotFound());
    mockMvc.perform(get("/jobs/2026-08-27/999999-missing.md")).andExpect(status().isNotFound());
  }

  @Test
  void legacyJobDetailForbidsMembersButAllowsAdministrators() throws Exception {
    Account member = member();
    Account admin = admin();
    Task task = legacyTasks.save(task("Legacy scoped job", Task.Status.completed));
    String url = legacyJobUrl(task);

    mockMvc.perform(get(url).with(user(member.loginName()))).andExpect(status().isForbidden());
    mockMvc
        .perform(get(url).with(user(admin.loginName())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Legacy scoped job")));
  }

  @Test
  void rendersQueuedRunningAndFailedLegacyJobStates() throws Exception {
    Task queued = legacyTasks.save(task("Queued legacy job", Task.Status.todo));
    mockMvc
        .perform(get(legacyJobUrl(queued)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Queued")))
        .andExpect(content().string(containsString("Waiting to start")));

    Task running = legacyTasks.save(task("Running legacy job", Task.Status.in_progress));
    mockMvc
        .perform(get(legacyJobUrl(running)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Running")));

    Task failed = legacyTasks.save(task("Failed legacy job", Task.Status.failed));
    mockMvc
        .perform(get(legacyJobUrl(failed)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Failed")))
        .andExpect(content().string(containsString("Stopped after failure")));
  }

  @Test
  void actorApprovalDecisionsAllowToolAndDenyForTheOwningMember() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Actor approval job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "MEMBER");

    mockMvc
        .perform(
            post("/jobs/" + reference.value() + "/approvals/" + approval.requestId() + "/allow")
                .with(user(owner.loginName()))
                .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/jobs/" + reference.value()));
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.todo);
  }

  @Test
  void actorApprovalDecisionsRejectUnknownRequestsAndConflicts() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Actor conflict job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "MEMBER");

    mockMvc
        .perform(
            post("/jobs/" + reference.value() + "/approvals/unknown-request/allow")
                .with(user(owner.loginName()))
                .with(csrf()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/jobs/" + reference.value() + "/approvals/" + approval.requestId() + "/deny")
                .with(user(owner.loginName()))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    mockMvc
        .perform(
            post("/jobs/" + reference.value() + "/approvals/" + approval.requestId() + "/deny")
                .with(user(owner.loginName()))
                .with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void memberCannotDecideAnApprovalCreatedForAnAdministratorRole() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Restricted approval job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "ADMIN");

    mockMvc
        .perform(
            post("/jobs/"
                    + reference.value()
                    + "/approvals/"
                    + approval.requestId()
                    + "/allow-tool")
                .with(user(owner.loginName()))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  void actorApprovalCannotCrossTheOwningActorBoundary() throws Exception {
    Account owner = member();
    Account other = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Owner approval job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "MEMBER");

    mockMvc
        .perform(
            post("/jobs/" + reference.value() + "/approvals/" + approval.requestId() + "/deny")
                .with(user(other.loginName()))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  void legacyApprovalDecisionsAllowToolAndDeny() throws Exception {
    Task task = legacyTasks.save(task("Legacy approval job", Task.Status.awaiting_human_input));
    String url = legacyJobUrl(task);
    SeaToolApprovalRequests.Entry approval = legacyApproval(task);

    mockMvc
        .perform(post(url + "/approvals/" + approval.requestId() + "/allow-tool").with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(url));
    mockMvc.perform(get(url)).andExpect(status().isOk());
  }

  @Test
  void legacyApprovalDecisionsRejectUnknownRequestsAndConflicts() throws Exception {
    Task task = legacyTasks.save(task("Legacy conflict job", Task.Status.awaiting_human_input));
    String url = legacyJobUrl(task);
    SeaToolApprovalRequests.Entry approval = legacyApproval(task);

    mockMvc
        .perform(post(url + "/approvals/unknown-request/allow").with(csrf()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(post(url + "/approvals/" + approval.requestId() + "/deny").with(csrf()))
        .andExpect(status().is3xxRedirection());
    mockMvc
        .perform(post(url + "/approvals/" + approval.requestId() + "/deny").with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void legacyApprovalCannotBeDecidedWhileTheJobIsRunning() throws Exception {
    Task task = legacyTasks.save(task("Legacy running job", Task.Status.in_progress));
    String url = legacyJobUrl(task);
    SeaToolApprovalRequests.Entry approval = legacyApproval(task);

    mockMvc
        .perform(post(url + "/approvals/" + approval.requestId() + "/allow").with(csrf()))
        .andExpect(status().isConflict());
  }

  private SeaToolApprovalRequests.Entry actorApproval(
      Account owner, Actor actor, ActorTaskReference reference, String accountRole) {
    ActorTaskExecutionReference execution = new ActorTaskExecutionReference(actor, reference);
    ZalavaProvider provider = scopedProvider();
    ZalavaToolDescriptor tool =
        new ZalavaToolDescriptor("write", "Writes scoped data", true, List.of("member-safe"));
    return approvals.create(
        provider,
        tool,
        new InvocationContext(
            owner.id().toString(),
            false,
            Map.of(
                "accountRole",
                accountRole,
                SeaToolApprovalRequests.ACTOR_TASK_REFERENCE,
                execution.encode())),
        JsonNodeFactory.instance.objectNode());
  }

  private SeaToolApprovalRequests.Entry legacyApproval(Task task) {
    ZalavaProvider provider = scopedProvider();
    ZalavaToolDescriptor tool =
        new ZalavaToolDescriptor("write", "Writes scoped data", true, List.of("member-safe"));
    return approvals.create(
        provider,
        tool,
        new InvocationContext("operator", false, Map.of("accountRole", "ADMIN")),
        JsonNodeFactory.instance.objectNode(),
        legacyTasks.getReference(task));
  }

  private Account member() {
    String login = "job-member-" + LOGINS.incrementAndGet();
    Account account = accounts.create(login, "TemporaryPassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "TemporaryPassword-123", "PermanentPassword-123");
    return accounts.findByLoginName(login).orElseThrow();
  }

  private Account admin() {
    String login = "job-admin-" + LOGINS.incrementAndGet();
    Account account = accounts.create(login, "TemporaryPassword-123", AccountRole.ADMIN);
    accounts.changePassword(account.id(), "TemporaryPassword-123", "PermanentPassword-123");
    return accounts.findByLoginName(login).orElseThrow();
  }

  private static Task task(String name, Task.Status status) {
    return new Task(null, name, Instant.now(), status, "Job detail component test task.");
  }

  private static String legacyJobUrl(Task task) {
    Path path = Path.of(task.getId());
    return "/jobs/" + path.getParent().getFileName() + "/" + path.getFileName();
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
    when(provider.callTool(
            org.mockito.ArgumentMatchers.eq("write"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(ZalavaOperationResult.success(Map.of("written", true)));
    return provider;
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("job-detail-component-test-");
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

  @TestConfiguration(proxyBeanMethods = false)
  static class RuntimeTestConfiguration {

    @Bean
    @Primary
    SeaRuntime loadedSeaRuntime() {
      ZalavaModule module =
          new ZalavaModule() {
            @Override
            public ModuleDescriptor descriptor() {
              return new ModuleDescriptor("test-module", "1.0.0", "Test Module", "Test module.");
            }

            @Override
            public List<org.zalava.api.ProviderFactory> providerFactories() {
              return List.of();
            }
          };
      ProviderFactoryDescriptor factory =
          new ProviderFactoryDescriptor("local-factory", "test-module", "test", "Test", "Test.");
      return new SeaRuntime() {
        @Override
        public List<org.zalava.api.ZalavaModule> modules() {
          return List.of(module);
        }

        @Override
        public List<LoadedSeaProvider> loadedProviders() {
          return List.of(new LoadedSeaProvider(module.descriptor(), factory, scopedProvider()));
        }

        @Override
        public void close() {}
      };
    }
  }
}
