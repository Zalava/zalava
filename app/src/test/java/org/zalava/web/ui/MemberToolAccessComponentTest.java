package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.api.InvocationContext;
import org.zalava.assistant.agent.AgentRequestTools;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolCallbackCatalog;
import org.zalava.capabilities.operation.adapter.out.jdbc.JdbcMemberToolAccessStore;
import org.zalava.capabilities.operation.adapter.out.runtime.ZalavaRuntimeProviderCatalog;
import org.zalava.capabilities.operation.application.DefaultMemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.support.SecureZalavaComponentTest;
import org.zalava.web.control.application.port.in.InvocationLogQueries;

@SecureZalavaComponentTest
@Import(MemberToolAccessFixture.class)
class MemberToolAccessComponentTest {
  @Autowired MockMvc mvc;
  @Autowired AccountLifecycle accounts;
  @Autowired MemberToolAccess access;
  @Autowired ProviderToolOperations operations;
  @Autowired JdbcClient jdbc;
  @Autowired ZalavaRuntime runtime;
  @Autowired InvocationLogQueries logs;
  @Autowired ActorExecutionContext actors;
  @Autowired AgentRequestTools agentTools;
  @Autowired ZalavaToolCallbackCatalog callbacks;
  Account admin, first, second;
  static final String PASSWORD = "MemberAccessPassword-123";

  @BeforeEach
  void prepare() {
    admin = account(AccountRole.ADMIN);
    first = account(AccountRole.MEMBER);
    second = account(AccountRole.MEMBER);
  }

  private Account account(AccountRole role) {
    var a = accounts.create("access-" + UUID.randomUUID(), PASSWORD, role);
    accounts.changePassword(a.id(), PASSWORD, PASSWORD + "-changed");
    return a;
  }

  private ToolKey key(String tool) {
    return new ToolKey(MemberToolAccessFixture.MODULE, MemberToolAccessFixture.PROVIDER, tool);
  }

  private String id(Account a) {
    return a.id().toString();
  }

  private String url() {
    return "/zalava/accounts/" + id(first) + "/tools";
  }

  private ProviderToolOperations.ToolInvocationOutcome invoke(Account a, String tool) {
    return operations.invoke(
        ProviderToolOperations.ToolInvocationCommand.operator(
            MemberToolAccessFixture.PROVIDER,
            tool,
            "{}",
            new InvocationContext(id(a), false, Map.of("accountRole", a.role().name()))));
  }

  @Test
  void htmlGrantIsExactPersistentRevocableAndEnforcedForEveryCall() throws Exception {
    assertThatThrownBy(() -> invoke(first, "current_time")).hasMessageContaining("denied");
    mvc.perform(
            post(url())
                .with(user(admin.loginName()).roles("ADMIN"))
                .with(csrf())
                .param("moduleId", key("current_time").moduleId())
                .param("providerId", key("current_time").providerId())
                .param("toolName", "current_time")
                .param("allowed", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("message", "Tool access granted."));
    assertThat(invoke(first, "current_time").status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThatThrownBy(() -> invoke(second, "current_time")).hasMessageContaining("denied");
    assertThatThrownBy(() -> invoke(first, "convert_time")).hasMessageContaining("denied");
    var reloaded =
        new DefaultMemberToolAccess(
            accounts,
            new JdbcMemberToolAccessStore(jdbc),
            new ZalavaRuntimeProviderCatalog(runtime),
            Clock.systemUTC());
    assertThat(reloaded.allowed(id(first), key("current_time"))).isTrue();
    access.decide(id(admin), id(first), key("current_time"), false);
    assertThatThrownBy(() -> invoke(first, "current_time")).hasMessageContaining("denied");
    assertThat(
            jdbc.sql("SELECT count(*) FROM zalava_member_tool_access_audit WHERE member_id=:member")
                .param("member", first.id().value())
                .query(Integer.class)
                .single())
        .isEqualTo(2);
    assertThat(logs.recentEntries())
        .anyMatch(
            entry ->
                entry.actorId().equals(id(first)) && "permission_denied".equals(entry.errorType()));
  }

  @Test
  void discoveryAndCachedCallbacksFollowGrantsAndRevocation() {
    var actor = new Actor(first.id());
    actors.call(
        actor,
        AccountRole.MEMBER,
        () -> {
          assertThat(callbacks.entries()).isEmpty();
          return null;
        });
    access.decide(id(admin), id(first), key("current_time"), true);
    actors.call(
        actor,
        AccountRole.MEMBER,
        () -> {
          assertThat(callbacks.entries())
              .extracting(value -> value.toolName())
              .containsExactly("current_time");
          assertThat(
                  agentTools
                      .resolve("test-grant-session", "current time", AccountRole.MEMBER)
                      .toolSummaries())
              .extracting(value -> value.toolName())
              .contains("current_time")
              .doesNotContain("convert_time");
          return null;
        });
    access.decide(id(admin), id(first), key("current_time"), false);
    actors.call(
        actor,
        AccountRole.MEMBER,
        () -> {
          assertThat(
                  agentTools
                      .resolve("test-grant-session", "current time", AccountRole.MEMBER)
                      .toolSummaries())
              .isEmpty();
          return null;
        });
  }

  @Test
  void safetyAndSideEffectApprovalsRemainIndependentOfGrant() {
    assertThatThrownBy(() -> access.decide(id(admin), id(first), key("unsafe_shell"), true))
        .hasMessageContaining("restricted");
    access.decide(id(admin), id(first), key("write_time"), true);
    assertThat(invoke(first, "write_time").status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL);
    assertThatThrownBy(() -> access.decide(id(first), id(second), key("current_time"), true))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> access.decide(id(admin), id(admin), key("current_time"), true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> access.decide(id(admin), id(first), key("missing"), true))
        .hasMessageContaining("not currently available");
  }

  @Test
  void revocationIsRecheckedWhenAnEarlierSideEffectApprovalIsAllowed() {
    access.decide(id(admin), id(first), key("write_time"), true);
    var pending = invoke(first, "write_time");
    access.decide(id(admin), id(first), key("write_time"), false);
    assertThatThrownBy(() -> operations.allowUnscoped(pending.approval().requestId()))
        .hasMessageContaining("denied");
  }

  @Test
  void currentAccountStateAndCsrfCannotBeBypassed() throws Exception {
    mvc.perform(get(url()).with(user(first.loginName()).roles("MEMBER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(url())
                .with(user(first.loginName()).roles("MEMBER"))
                .with(csrf())
                .param("moduleId", key("current_time").moduleId())
                .param("providerId", key("current_time").providerId())
                .param("toolName", "current_time")
                .param("allowed", "true"))
        .andExpect(status().isForbidden());
    mvc.perform(post(url()).with(user(admin.loginName()).roles("ADMIN")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/zalava/accounts/not-a-uuid/tools").with(user(admin.loginName()).roles("ADMIN")))
        .andExpect(status().isNotFound());
    access.decide(id(admin), id(first), key("current_time"), true);
    accounts.setEnabled(first.id(), false);
    assertThatThrownBy(() -> invoke(first, "current_time")).hasMessageContaining("denied");
  }

  @Test
  void renderedPageShowsCurrentDecisionsAndSafeErrors() throws Exception {
    String html =
        mvc.perform(get(url()).with(user(admin.loginName()).roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(html)
        .contains("Tool access for", "Grant access", "Restricted by host policy", "Not allowed");
    mvc.perform(
            post(url())
                .with(user(admin.loginName()).roles("ADMIN"))
                .with(csrf())
                .param("moduleId", key("missing").moduleId())
                .param("providerId", key("missing").providerId())
                .param("toolName", "missing")
                .param("allowed", "true"))
        .andExpect(flash().attributeExists("error"));
  }
}
