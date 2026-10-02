package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import org.zalava.support.SecureSeaComponentTest;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import tools.jackson.databind.node.JsonNodeFactory;

@SecureSeaComponentTest
@ResourceLock("secure-component-runtime")
class ProductIsolationComponentTest {
  private static final AtomicInteger LOGINS = new AtomicInteger();

  @Autowired MockMvc mockMvc;
  @Autowired AccountLifecycle accounts;
  @Autowired ActorTaskStore tasks;
  @Autowired SeaToolApprovalRequests approvals;

  @Test
  void productRoutesRequireAuthenticationAndPermitPersistedMembers() throws Exception {
    Account member = member();

    mockMvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    mockMvc
        .perform(get("/dashboard").with(user(member.loginName()).roles("MEMBER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/chat").with(user(member.loginName()).roles("MEMBER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/jobs/2026-08-27/legacy-task.md").with(user(member.loginName()).roles("MEMBER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void twoMembersCannotListOrFetchEachOthersJobs() throws Exception {
    Account first = member();
    Account second = member();
    Actor firstActor = new Actor(first.id());
    Actor secondActor = new Actor(second.id());
    ActorTaskReference firstReference = ActorTaskReference.newReference();
    ActorTaskReference secondReference = ActorTaskReference.newReference();
    tasks.save(firstActor, firstReference, task("First private job", Task.Status.completed));
    tasks.save(secondActor, secondReference, task("Second private job", Task.Status.completed));

    mockMvc
        .perform(get("/jobs").with(user(first.loginName()).roles("MEMBER")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("First private job")))
        .andExpect(content().string(not(containsString("Second private job"))));
    mockMvc
        .perform(
            get("/jobs/" + secondReference.value()).with(user(first.loginName()).roles("MEMBER")))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/dashboard").with(user(first.loginName()).roles("MEMBER")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("First private job")))
        .andExpect(content().string(not(containsString("Second private job"))));
  }

  @Test
  void approvalRequestIdCannotCrossTheOwningActorAndTaskBoundary() throws Exception {
    Account owner = member();
    Account other = member();
    Actor actor = new Actor(owner.id());
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(actor, reference, task("Approval job", Task.Status.awaiting_human_input));
    ActorTaskExecutionReference execution = new ActorTaskExecutionReference(actor, reference);
    ZalavaProvider provider = provider();
    ZalavaToolDescriptor tool =
        new ZalavaToolDescriptor("write", "Writes scoped data", true, List.of("member-safe"));
    var approval =
        approvals.create(
            provider,
            tool,
            new InvocationContext(
                actor.accountId().toString(),
                false,
                Map.of(
                    "accountRole",
                    "MEMBER",
                    SeaToolApprovalRequests.ACTOR_TASK_REFERENCE,
                    execution.encode())),
            JsonNodeFactory.instance.objectNode());

    mockMvc
        .perform(
            post("/jobs/" + reference.value() + "/approvals/" + approval.requestId() + "/deny")
                .with(user(other.loginName()).roles("MEMBER"))
                .with(csrf()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/jobs/" + reference.value() + "/approvals/" + approval.requestId() + "/deny")
                .with(user(owner.loginName()).roles("MEMBER"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/jobs/" + reference.value()));
    assertThat(tasks.get(actor, reference).getStatus()).isEqualTo(Task.Status.todo);
  }

  @Test
  void rejectsACrossOriginWebSocketHandshake() throws Exception {
    Account member = member();

    mockMvc
        .perform(
            get("/ws/chat")
                .with(user(member.loginName()).roles("MEMBER"))
                .header(HttpHeaders.ORIGIN, "https://attacker.example")
                .header(HttpHeaders.CONNECTION, "Upgrade")
                .header(HttpHeaders.UPGRADE, "websocket")
                .header("Sec-WebSocket-Version", "13")
                .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))
        .andExpect(status().isForbidden());
  }

  private Account member() {
    String login = "member-" + LOGINS.incrementAndGet();
    Account account = accounts.create(login, "TemporaryPassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "TemporaryPassword-123", "PermanentPassword-123");
    return accounts.findByLoginName(login).orElseThrow();
  }

  private static Task task(String name, Task.Status status) {
    return new Task(null, name, Instant.now(), status, "Private component test task.");
  }

  private static ZalavaProvider provider() {
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
    return provider;
  }
}
