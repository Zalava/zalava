package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.api.InvocationContext;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations.ToolInvocationCommand;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations.ToolInvocationStatus;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.SecureZalavaComponentTest;

@SecureZalavaComponentTest
@Import(MemberToolAccessFixture.class)
class ChatConfirmationComponentTest {
  @Autowired MockMvc mvc;
  @Autowired AccountLifecycle accounts;
  @Autowired ChatToolConfirmation confirmation;
  @Autowired MemberToolAccess access;
  @Autowired ProviderToolOperations operations;
  @Autowired JdbcClient jdbc;
  Account admin, first, second;
  ToolKey write =
      new ToolKey(MemberToolAccessFixture.MODULE, MemberToolAccessFixture.PROVIDER, "write_time");
  static final String PASSWORD = "ConfirmationPolicyPassword-123";

  @BeforeEach
  void prepare() {
    admin = account(AccountRole.ADMIN);
    first = account(AccountRole.MEMBER);
    second = account(AccountRole.MEMBER);
    confirmation.setRole(id(admin), AccountRole.MEMBER, write, Choice.INHERIT);
    confirmation.setRole(id(admin), AccountRole.ADMIN, write, Choice.INHERIT);
    access.defaultAccess(id(admin), write, false);
  }

  private Account account(AccountRole role) {
    Account a = accounts.create("confirmation-" + UUID.randomUUID(), PASSWORD, role);
    accounts.changePassword(a.id(), PASSWORD, PASSWORD + "-changed");
    return a;
  }

  private static String id(Account a) {
    return a.id().toString();
  }

  private ToolInvocationCommand command(Account actor, String channel, String argument) {
    return ToolInvocationCommand.task(
        write.providerId(),
        write.toolName(),
        "{\"value\":\"" + argument + "\"}",
        new InvocationContext(
            id(actor),
            false,
            Map.of(
                "accountRole",
                actor.role().name(),
                "permissionPolicyChannel",
                channel,
                "source",
                "spring-ai-callback")),
        null);
  }

  @Test
  void roleAndPersonOverridesAreAuditedProtectedAndEnforcedOnlyForChat() throws Exception {
    mvc.perform(
            get("/zalava/accounts/chat-confirmations").with(user(admin.loginName()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Chat tool confirmation")))
        .andExpect(content().string(containsString("write_time")));
    mvc.perform(
            get("/zalava/accounts/chat-confirmations")
                .with(user(first.loginName()).roles("MEMBER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/zalava/accounts/chat-confirmations/role")
                .with(user(admin.loginName()).roles("ADMIN"))
                .param("role", "MEMBER")
                .param("choice", "AUTO")
                .param("moduleId", write.moduleId())
                .param("providerId", write.providerId())
                .param("toolName", write.toolName()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/zalava/accounts/chat-confirmations/role")
                .with(user(admin.loginName()).roles("ADMIN"))
                .with(csrf())
                .param("role", "MEMBER")
                .param("choice", "AUTO")
                .param("moduleId", write.moduleId())
                .param("providerId", write.providerId())
                .param("toolName", write.toolName()))
        .andExpect(status().is3xxRedirection());
    assertThat(confirmation.effective(id(admin), id(first), write).effective())
        .isEqualTo(Choice.AUTO);
    assertThatThrownBy(() -> operations.invoke(command(first, "web", "blocked")))
        .hasMessageContaining("denied");
    access.defaultAccess(id(admin), write, true);
    assertThat(operations.invoke(command(first, "web", "allowed")).status())
        .isEqualTo(ToolInvocationStatus.EXECUTED);
    assertThat(operations.invoke(command(second, "telegram", "other-channel")).status())
        .isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    mvc.perform(
            post("/zalava/accounts/chat-confirmations/user")
                .with(user(admin.loginName()).roles("ADMIN"))
                .with(csrf())
                .param("accountId", id(first))
                .param("choice", "REQUIRE")
                .param("moduleId", write.moduleId())
                .param("providerId", write.providerId())
                .param("toolName", write.toolName()))
        .andExpect(status().is3xxRedirection());
    assertThat(confirmation.effective(id(admin), id(first), write).source())
        .isEqualTo("User override");
    assertThat(operations.invoke(command(first, "web", "require")).status())
        .isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    assertThat(operations.invoke(command(second, "web", "other-person")).status())
        .isEqualTo(ToolInvocationStatus.EXECUTED);
    confirmation.setUser(id(admin), id(first), write, Choice.INHERIT);
    assertThat(confirmation.effective(id(admin), id(first), write).source())
        .isEqualTo("Role default");
    assertThat(operations.invoke(command(first, "web", "inherited")).status())
        .isEqualTo(ToolInvocationStatus.EXECUTED);
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM zalava_chat_confirmation_audit WHERE module_id=:module AND tool_name=:tool")
                .param("module", write.moduleId())
                .param("tool", write.toolName())
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(5);
    mvc.perform(
            get("/zalava/accounts/chat-confirmations")
                .param("accountId", id(first))
                .with(user(admin.loginName()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Role default")));
    accounts.setEnabled(first.id(), false);
    assertThatThrownBy(() -> operations.invoke(command(first, "web", "disabled")))
        .hasMessageContaining("denied");
  }

  @Test
  void explicitRequireOverridesLegacyAlwaysAllowAndRoleChangesReevaluate() {
    access.defaultAccess(id(admin), write, true);
    var pending = operations.invoke(command(first, "web", "same"));
    assertThat(pending.status()).isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    operations.allowUnscopedTool(pending.approval().requestId());
    assertThat(operations.invoke(command(first, "web", "legacy-allowed")).status())
        .isEqualTo(ToolInvocationStatus.EXECUTED);
    confirmation.setRole(id(admin), AccountRole.MEMBER, write, Choice.REQUIRE);
    assertThat(operations.invoke(command(first, "web", "same")).status())
        .isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    confirmation.setUser(id(admin), id(first), write, Choice.AUTO);
    assertThat(operations.invoke(command(first, "web", "person-auto")).status())
        .isEqualTo(ToolInvocationStatus.EXECUTED);
    confirmation.setUser(id(admin), id(first), write, Choice.INHERIT);
    confirmation.setRole(id(admin), AccountRole.ADMIN, write, Choice.AUTO);
    accounts.setRole(first.id(), AccountRole.ADMIN);
    Account promoted = accounts.findById(first.id()).orElseThrow();
    assertThat(operations.invoke(command(promoted, "web", "promoted")).status())
        .isEqualTo(ToolInvocationStatus.EXECUTED);
    confirmation.setUser(id(admin), id(first), write, Choice.REQUIRE);
    assertThat(operations.invoke(command(promoted, "web", "person-override")).status())
        .isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
  }

  @Test
  void invalidAndUnauthorisedSettingsNeverGrantExecution() throws Exception {
    assertThatThrownBy(
            () -> confirmation.setRole(id(first), AccountRole.MEMBER, write, Choice.AUTO))
        .isInstanceOf(SecurityException.class);
    ToolKey read = new ToolKey(write.moduleId(), write.providerId(), "current_time");
    assertThatThrownBy(() -> confirmation.setRole(id(admin), AccountRole.MEMBER, read, Choice.AUTO))
        .isInstanceOf(IllegalArgumentException.class);
    mvc.perform(
            post("/zalava/accounts/chat-confirmations/role")
                .with(user(first.loginName()).roles("MEMBER"))
                .with(csrf())
                .param("role", "MEMBER")
                .param("choice", "AUTO")
                .param("moduleId", write.moduleId())
                .param("providerId", write.providerId())
                .param("toolName", write.toolName()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/zalava/accounts/chat-confirmations/role")
                .with(user(admin.loginName()).roles("ADMIN"))
                .with(csrf())
                .param("role", "MEMBER")
                .param("choice", "INVALID")
                .param("moduleId", write.moduleId())
                .param("providerId", write.providerId())
                .param("toolName", write.toolName()))
        .andExpect(status().is3xxRedirection());
    assertThat(confirmation.roleDefault(id(admin), AccountRole.MEMBER, write))
        .isEqualTo(Choice.INHERIT);
  }
}
