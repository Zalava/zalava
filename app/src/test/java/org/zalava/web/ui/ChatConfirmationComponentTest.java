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
import org.zalava.assistant.agent.AgentRequestTools;
import org.zalava.assistant.agent.ConversationChannelContext;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.HostReadToolCatalog;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations.ToolInvocationCommand;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations.ToolInvocationStatus;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.adapter.in.agent.KnowledgeAgentTools;
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
  @Autowired AgentRequestTools agentTools;
  @Autowired ActorExecutionContext actorContext;
  @Autowired ZalavaToolApprovalRequests approvals;
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

  @Test
  void knowledgeReadConfirmationUsesApplicationWiringAndPersonOverride() throws Exception {
    ToolKey key =
        new ToolKey(HostReadToolCatalog.MODULE, HostReadToolCatalog.PROVIDER, "knowledge.search");
    var knowledge =
        agentTools.bootstrapTools().stream()
            .filter(KnowledgeAgentTools.class::isInstance)
            .map(KnowledgeAgentTools.class::cast)
            .findFirst()
            .orElseThrow();
    try {
      mvc.perform(get("/zalava/accounts/permissions").with(user(admin.loginName()).roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("knowledge.search")))
          .andExpect(content().string(containsString("Governed by document permissions")));
      mvc.perform(
              post("/zalava/accounts/permissions/confirmation")
                  .with(user(admin.loginName()).roles("ADMIN"))
                  .with(csrf())
                  .param("subject", id(first))
                  .param("moduleId", key.moduleId())
                  .param("providerId", key.providerId())
                  .param("toolName", key.toolName())
                  .param("decision", "REQUIRE"))
          .andExpect(status().is3xxRedirection());
      String pending =
          actorContext.call(
              new Actor(first.id()),
              AccountRole.MEMBER,
              () ->
                  ConversationChannelContext.call(
                      "chat-knowledge", () -> knowledge.search("unique-regression-query", 2)));
      assertThat(pending).contains("pending_approval").doesNotContain("NO_MATCH");
      var entry =
          approvals.recentEntries().stream()
              .filter(e -> e.actorId().equals(id(first)) && e.toolName().equals(key.toolName()))
              .findFirst()
              .orElseThrow();
      operations.approveChatRead(entry.requestId());
      String result =
          actorContext.call(
              new Actor(first.id()),
              AccountRole.MEMBER,
              () ->
                  ConversationChannelContext.call(
                      "chat-knowledge", () -> knowledge.search("unique-regression-query", 2)));
      assertThat(result).contains("NO_MATCH");
      String defaultResult =
          actorContext.call(
              new Actor(second.id()),
              AccountRole.MEMBER,
              () ->
                  ConversationChannelContext.call(
                      "chat-other", () -> knowledge.search("unique-regression-query", 2)));
      assertThat(defaultResult).contains("NO_MATCH");
    } finally {
      confirmation.setUser(id(admin), id(first), key, Choice.INHERIT);
    }
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
  void readOnlyToolsDefaultToAutomaticButCanRequireChatApproval() throws Exception {
    ToolKey read = new ToolKey(write.moduleId(), write.providerId(), "current_time");
    access.defaultAccess(id(admin), read, true);
    confirmation.setRole(id(admin), AccountRole.MEMBER, read, Choice.INHERIT);
    var context =
        new InvocationContext(
            id(first),
            false,
            Map.of(
                "accountRole",
                "MEMBER",
                "permissionPolicyChannel",
                "web",
                "source",
                "spring-ai-callback"));
    var chat = ToolInvocationCommand.task(read.providerId(), read.toolName(), "{}", context, null);
    assertThat(operations.invoke(chat).status()).isEqualTo(ToolInvocationStatus.EXECUTED);
    confirmation.setRole(id(admin), AccountRole.MEMBER, read, Choice.REQUIRE);
    var pending = operations.invoke(chat);
    assertThat(pending.status()).isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    assertThat(pending.result()).isNull();
    assertThat(operations.allowUnscoped(pending.approval().requestId()).status())
        .isEqualTo(ToolInvocationStatus.EXECUTED);
    confirmation.setUser(id(admin), id(first), read, Choice.AUTO);
    assertThat(operations.invoke(chat).status()).isEqualTo(ToolInvocationStatus.EXECUTED);
    confirmation.setUser(id(admin), id(first), read, Choice.INHERIT);
    var denied = operations.invoke(chat);
    assertThat(denied.status()).isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    assertThat(denied.result()).isNull();
    assertThat(operations.denyUnscoped(denied.approval().requestId()).decision().name())
        .isEqualTo("DENIED");
    var revoked = operations.invoke(chat);
    access.decide(id(admin), id(first), read, false);
    assertThatThrownBy(() -> operations.allowUnscoped(revoked.approval().requestId()))
        .hasMessageContaining("denied");
    access.inherit(id(admin), id(first), read);
    var telegram =
        ToolInvocationCommand.task(
            read.providerId(),
            read.toolName(),
            "{}",
            new InvocationContext(
                id(first),
                false,
                Map.of(
                    "accountRole",
                    "MEMBER",
                    "permissionPolicyChannel",
                    "telegram",
                    "source",
                    "spring-ai-callback")),
            null);
    assertThat(operations.invoke(telegram).status()).isEqualTo(ToolInvocationStatus.EXECUTED);
    mvc.perform(
            get("/zalava/accounts/chat-confirmations").with(user(admin.loginName()).roles("ADMIN")))
        .andExpect(content().string(containsString("current_time")));
    confirmation.setRole(id(admin), AccountRole.MEMBER, read, Choice.INHERIT);
  }

  @Test
  void approvedReadMayBeUsedOnceOnlyForSameConversationAndArguments() {
    ToolKey read = new ToolKey(write.moduleId(), write.providerId(), "current_time");
    access.defaultAccess(id(admin), read, true);
    confirmation.setRole(id(admin), AccountRole.MEMBER, read, Choice.REQUIRE);
    var context =
        new InvocationContext(
            id(first),
            false,
            Map.of(
                "accountRole",
                "MEMBER",
                "permissionPolicyChannel",
                "web",
                "source",
                "spring-ai-callback",
                "conversationId",
                "web-conversation-a"));
    var same = ToolInvocationCommand.task(read.providerId(), read.toolName(), "{}", context, null);
    var pending = operations.invoke(same);
    assertThat(pending.status()).isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    operations.approveChatRead(pending.approval().requestId());
    var otherContext =
        new InvocationContext(
            id(first),
            false,
            Map.of(
                "accountRole",
                "MEMBER",
                "permissionPolicyChannel",
                "web",
                "source",
                "spring-ai-callback",
                "conversationId",
                "web-conversation-b"));
    var other =
        ToolInvocationCommand.task(read.providerId(), read.toolName(), "{}", otherContext, null);
    assertThat(operations.invoke(other).status()).isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    var otherArgs =
        ToolInvocationCommand.task(
            read.providerId(), read.toolName(), "{\"timezone\":\"UTC\"}", context, null);
    assertThat(operations.invoke(otherArgs).status())
        .isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    var result = operations.invoke(same);
    assertThat(result.status()).isEqualTo(ToolInvocationStatus.EXECUTED);
    assertThat(result.result().content().toString()).contains("2026-10-26T12:00:00Z");
    assertThat(operations.invoke(same).status()).isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
    confirmation.setRole(id(admin), AccountRole.MEMBER, read, Choice.INHERIT);
    confirmation.setUser(id(admin), id(admin), read, Choice.REQUIRE);
    var adminContext =
        new InvocationContext(
            id(admin),
            false,
            Map.of(
                "accountRole",
                "ADMIN",
                "permissionPolicyChannel",
                "web",
                "source",
                "spring-ai-callback",
                "conversationId",
                "web-admin"));
    var adminRead =
        ToolInvocationCommand.task(read.providerId(), read.toolName(), "{}", adminContext, null);
    var adminPending = operations.invoke(adminRead);
    operations.approveChatRead(adminPending.approval().requestId());
    accounts.setEnabled(admin.id(), false);
    assertThat(operations.invoke(adminRead).status())
        .isEqualTo(ToolInvocationStatus.PENDING_APPROVAL);
  }

  @Test
  void unifiedPermissionsManageRoleAndIndividualWithoutGrantingToolAccessImplicitly()
      throws Exception {
    ToolKey read = new ToolKey(write.moduleId(), write.providerId(), "current_time");
    var adminUser = user(admin.loginName()).roles("ADMIN");
    var memberUser = user(first.loginName()).roles("MEMBER");
    mvc.perform(get("/zalava/accounts/permissions").with(memberUser))
        .andExpect(status().isForbidden());
    mvc.perform(get("/zalava/accounts/permissions").with(adminUser))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Read time")))
        .andExpect(content().string(containsString("Current time")))
        .andExpect(content().string(containsString("Default: no confirmation")));
    mvc.perform(
            post("/zalava/accounts/permissions/confirmation")
                .with(adminUser)
                .param("subject", "member")
                .param("moduleId", read.moduleId())
                .param("providerId", read.providerId())
                .param("toolName", read.toolName())
                .param("decision", "REQUIRE"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/zalava/accounts/permissions/confirmation")
                .with(adminUser)
                .with(csrf())
                .param("subject", "member")
                .param("moduleId", read.moduleId())
                .param("providerId", read.providerId())
                .param("toolName", read.toolName())
                .param("decision", "REQUIRE"))
        .andExpect(status().is3xxRedirection());
    assertThat(confirmation.roleDefault(id(admin), AccountRole.MEMBER, read))
        .isEqualTo(Choice.REQUIRE);
    assertThat(access.allowed(id(first), read)).isFalse();
    mvc.perform(
            post("/zalava/accounts/permissions/tool-access")
                .with(adminUser)
                .with(csrf())
                .param("subject", id(first))
                .param("moduleId", read.moduleId())
                .param("providerId", read.providerId())
                .param("toolName", read.toolName())
                .param("decision", "ALLOW"))
        .andExpect(status().is3xxRedirection());
    assertThat(access.allowed(id(first), read)).isTrue();
    mvc.perform(
            post("/zalava/accounts/permissions/confirmation")
                .with(adminUser)
                .with(csrf())
                .param("subject", id(first))
                .param("moduleId", read.moduleId())
                .param("providerId", read.providerId())
                .param("toolName", read.toolName())
                .param("decision", "AUTO"))
        .andExpect(status().is3xxRedirection());
    mvc.perform(get("/zalava/accounts/permissions").with(adminUser).param("subject", id(first)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Run without confirmation")))
        .andExpect(content().string(containsString("User override")));
    assertThat(confirmation.effective(id(admin), id(first), read).effective())
        .isEqualTo(Choice.AUTO);
    confirmation.setRole(id(admin), AccountRole.MEMBER, read, Choice.INHERIT);
  }

  @Test
  void invalidAndUnauthorisedSettingsNeverGrantExecution() throws Exception {
    assertThatThrownBy(
            () -> confirmation.setRole(id(first), AccountRole.MEMBER, write, Choice.AUTO))
        .isInstanceOf(SecurityException.class);
    ToolKey read = new ToolKey(write.moduleId(), write.providerId(), "missing_tool");
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
