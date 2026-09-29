package org.zalava.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.InvocationContext;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.runtime.LoadedSeaProvider;
import org.zalava.runtime.SeaRuntime;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Full-context WebSocket component test for the interactive approval authority. It drives the real
 * {@link UiChatWebSocketHandler} with the real actor resolver, approval store and task commands, so
 * it proves the ownership, pending-state, role and resume boundaries the mock-based handler tests
 * cannot see.
 */
@SpringBootTest
@Import(UiChatWebSocketApprovalComponentTest.RuntimeTestConfiguration.class)
class UiChatWebSocketApprovalComponentTest {
  private static final Path WORKSPACE = createWorkspace();
  private static final AtomicInteger LOGINS = new AtomicInteger();

  @Autowired private UiChatWebSocketHandler handler;
  @Autowired private AccountLifecycle accounts;
  @Autowired private ActorTaskStore tasks;
  @Autowired private SeaToolApprovalRequests approvals;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> "ui-interact-component-admin");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void ownerCanAllowOnceAndTheAwaitingJobResumes() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Allow once job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "MEMBER");

    WebSocketSession session = session(owner.loginName());
    handler.handleTextMessage(
        session, command(reference.value(), approval.requestId(), "allow-once"));

    assertThat(payloads(session)).contains("job.updated").contains("todo");
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.todo);
  }

  @Test
  void ownerCanDenyAndTheAwaitingJobResumes() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Deny job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "MEMBER");

    WebSocketSession session = session(owner.loginName());
    handler.handleTextMessage(session, command(reference.value(), approval.requestId(), "deny"));

    assertThat(payloads(session)).contains("job.updated").contains("todo");
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.todo);
  }

  @Test
  void malformedDecisionIsRejectedWithoutChangingTheJob() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Malformed job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "MEMBER");

    WebSocketSession session = session(owner.loginName());
    handler.handleTextMessage(session, command(reference.value(), approval.requestId(), "maybe"));

    assertThat(payloads(session))
        .contains("failure")
        .contains("Unsupported or invalid UI command")
        .doesNotContain("job.updated");
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.awaiting_human_input);
  }

  @Test
  void alreadyDecidedJobCannotBeDecidedAgain() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Already decided job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "MEMBER");

    WebSocketSession first = session(owner.loginName());
    handler.handleTextMessage(first, command(reference.value(), approval.requestId(), "deny"));
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.todo);

    WebSocketSession second = session(owner.loginName());
    handler.handleTextMessage(second, command(reference.value(), approval.requestId(), "deny"));

    assertThat(payloads(second))
        .contains("failure")
        .contains("SEA could not complete that request")
        .doesNotContain("job.updated");
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.todo);
  }

  @Test
  void anotherActorCannotDecideAnOwnersApproval() throws Exception {
    Account owner = member();
    Account other = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Owner approval job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "MEMBER");

    WebSocketSession session = session(other.loginName());
    handler.handleTextMessage(session, command(reference.value(), approval.requestId(), "deny"));

    assertThat(payloads(session))
        .contains("failure")
        .contains("SEA could not complete that request")
        .doesNotContain("job.updated");
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.awaiting_human_input);
    assertThat(approvals.get(actor, reference, approval.requestId()).decision())
        .isEqualTo(SeaToolApprovalRequests.Decision.PENDING);
  }

  @Test
  void memberCannotDecideAnApprovalCreatedForAnAdministratorRole() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Restricted approval job", Task.Status.awaiting_human_input));
    SeaToolApprovalRequests.Entry approval = actorApproval(owner, actor, reference, "ADMIN");

    WebSocketSession session = session(owner.loginName());
    handler.handleTextMessage(
        session, command(reference.value(), approval.requestId(), "allow-once"));

    assertThat(payloads(session)).contains("failure").doesNotContain("job.updated");
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.awaiting_human_input);
  }

  private static TextMessage command(String jobId, String requestId, String decision)
      throws IOException {
    return new TextMessage(
        "{\"protocol\":\"sea.ui/v1\",\"type\":\"approval.decide\",\"jobId\":\""
            + jobId
            + "\",\"requestId\":\""
            + requestId
            + "\",\"decision\":\""
            + decision
            + "\"}");
  }

  private static WebSocketSession session(String login) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getPrincipal()).thenReturn((Principal) () -> login);
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private static String payloads(WebSocketSession session) {
    ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
    try {
      verify(session, atLeastOnce()).sendMessage(sent.capture());
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
    return sent.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat);
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

  private Account member() {
    String login = "ui-interact-member-" + LOGINS.incrementAndGet();
    Account account = accounts.create(login, "TemporaryPassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "TemporaryPassword-123", "PermanentPassword-123");
    return accounts.findByLoginName(login).orElseThrow();
  }

  private static Task task(String name, Task.Status status) {
    return new Task(null, name, Instant.now(), status, "Interactive approval component test task.");
  }

  private static ZalavaProvider scopedProvider() {
    ZalavaProvider provider = mock(ZalavaProvider.class);
    ProviderDescriptor descriptor =
        new ProviderDescriptor(
            "ui-interact-provider",
            "test-module",
            "test",
            "Interactive provider",
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
      Path workspace = Files.createTempDirectory("ui-interact-component-test-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
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
            public List<org.zalava.ProviderFactory> providerFactories() {
              return List.of();
            }
          };
      ProviderFactoryDescriptor factory =
          new ProviderFactoryDescriptor("local-factory", "test-module", "test", "Test", "Test.");
      return new SeaRuntime() {
        @Override
        public List<org.zalava.ZalavaModule> modules() {
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
