package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.api.InvocationContext;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.assistant.agent.ConversationChannelContext;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore.Evidence;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.InvocationChannelContext;
import org.zalava.support.AuthenticatedZalavaComponentTest;
import org.zalava.support.ComponentTestAccounts;
import org.zalava.web.control.application.InvocationLog;
import org.zalava.web.control.application.port.in.InvocationLogQueries.Entry;
import tools.jackson.databind.node.JsonNodeFactory;

@AuthenticatedZalavaComponentTest
class ConversationEvidenceComponentTest {
  @Autowired MockMvc mvc;
  @Autowired ComponentTestAccounts accounts;
  @Autowired ActorChatCommands conversations;
  @Autowired AgentRunStore runs;
  @Autowired InvocationLog calls;
  @Autowired ZalavaToolApprovalRequests approvals;
  @Autowired StandingHomeActionEvidenceStore standingHomeActions;

  @Test
  void retainedStandingGrantExecutionIsOwnerScopedAndDeduplicated() throws Exception {
    var owner = accounts.newActivated(AccountRole.MEMBER);
    var outsider = accounts.newActivated(AccountRole.MEMBER);
    var actor = new Actor(owner.id());
    var conversation = conversations.createWebConversation(actor);
    String internal = new ActorConversationId(actor, conversation).value();
    String runId = UUID.randomUUID().toString();
    String executionId = UUID.randomUUID().toString();
    standingHomeActions.record(
        actor,
        new Evidence(
            Instant.now().toString(),
            internal,
            runId,
            executionId,
            "light",
            "turn_on",
            "light.room",
            true,
            23));
    String route = "/chat/" + conversation.value() + "/evidence";
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tools.length()").value(1))
        .andExpect(jsonPath("$.tools[0].runId").value(runId))
        .andExpect(jsonPath("$.tools[0].actionOutcome").value("accepted"))
        .andExpect(jsonPath("$.tools[0].approvalStatus").value(""))
        .andExpect(jsonPath("$.tools[0].durationMillis").value(23))
        .andExpect(jsonPath("$.tools[0].input").value(containsString("Other parameters omitted")));
    mvc.perform(get(route).with(accounts.authenticatedAs(outsider)))
        .andExpect(status().isNotFound());
    calls.record(
        new Entry(
            Instant.now().toString(),
            "home-assistant",
            "home_assistant_execute_action",
            owner.id().toString(),
            true,
            "home",
            List.of(),
            true,
            true,
            null,
            null,
            "accepted",
            23,
            internal,
            "{\"domain\":\"light\",\"action\":\"turn_on\",\"entityId\":\"light.room\"}",
            runId,
            executionId));
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(jsonPath("$.tools.length()").value(1))
        .andExpect(jsonPath("$.tools[0].actionOutcome").value("accepted"));
    standingHomeActions.record(
        actor,
        new Evidence(
            Instant.now().toString(),
            internal,
            runId,
            UUID.randomUUID().toString(),
            "light",
            "turn_on",
            "light.room",
            true,
            24));
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(jsonPath("$.tools.length()").value(2));
  }

  @Test
  void standingActionEvidenceOrdersFractionalTimestampsChronologically() throws Exception {
    var owner = accounts.newActivated(AccountRole.MEMBER);
    var actor = new Actor(owner.id());
    var conversation = conversations.createWebConversation(actor);
    String internal = new ActorConversationId(actor, conversation).value();
    String older = UUID.randomUUID().toString();
    String newer = UUID.randomUUID().toString();
    standingHomeActions.record(
        actor,
        new Evidence(
            "2026-01-01T00:00:00.1Z",
            internal,
            older,
            UUID.randomUUID().toString(),
            "light",
            "turn_on",
            "light.room",
            true,
            1));
    standingHomeActions.record(
        actor,
        new Evidence(
            "2026-01-01T00:00:00.19Z",
            internal,
            newer,
            UUID.randomUUID().toString(),
            "light",
            "turn_on",
            "light.room",
            true,
            1));
    mvc.perform(
            get("/chat/" + conversation.value() + "/evidence")
                .with(accounts.authenticatedAs(owner)))
        .andExpect(jsonPath("$.tools[0].runId").value(newer))
        .andExpect(jsonPath("$.tools[1].runId").value(older));
  }

  @Test
  void retainedUnsuccessfulStandingActionDoesNotClaimPhysicalFailure() throws Exception {
    var owner = accounts.newActivated(AccountRole.MEMBER);
    var actor = new Actor(owner.id());
    var conversation = conversations.createWebConversation(actor);
    String internal = new ActorConversationId(actor, conversation).value();
    standingHomeActions.record(
        actor,
        new Evidence(
            Instant.now().toString(),
            internal,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "light",
            "turn_on",
            "light.room",
            false,
            15));
    mvc.perform(
            get("/chat/" + conversation.value() + "/evidence")
                .with(accounts.authenticatedAs(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tools[0].actionOutcome").value("unknown"))
        .andExpect(jsonPath("$.tools[0].approvalStatus").value(""))
        .andExpect(
            jsonPath("$.tools[0].result").value(containsString("Check its state before retrying")));
  }

  @Test
  void retainedHomeActionApprovalIsOwnerScopedAndDoesNotExposeServiceData() throws Exception {
    var owner = accounts.newActivated(AccountRole.MEMBER);
    var outsider = accounts.newActivated(AccountRole.MEMBER);
    var actor = new Actor(owner.id());
    var conversation = conversations.createWebConversation(actor);
    String internal = new ActorConversationId(actor, conversation).value();
    var provider = mock(ZalavaProvider.class);
    when(provider.descriptor())
        .thenReturn(
            new ProviderDescriptor(
                "home-assistant",
                "zalava-module-home-assistant",
                "home",
                "Home Assistant",
                "Fixture",
                "1",
                null,
                List.of(),
                Map.of()));
    var arguments = JsonNodeFactory.instance.objectNode();
    arguments.put("domain", "light");
    arguments.put("action", "turn_on");
    arguments.put("entityId", "light.room");
    arguments.putObject("serviceData").put("private_note", "do-not-expose");
    var request =
        approvals.create(
            provider,
            new ZalavaToolDescriptor("home_assistant_execute_action", "Execute", true),
            new InvocationContext(
                owner.id().toString(),
                false,
                Map.of(
                    "conversationId",
                    internal,
                    "permissionPolicyChannel",
                    "web",
                    InvocationChannelContext.RUN_ID,
                    "retained-home-run")),
            arguments);
    approvals.allowUnscoped(request.requestId());
    approvals.recordExecutionOutcome(request.requestId(), true);
    String route = "/chat/" + conversation.value() + "/evidence";
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tools.length()").value(1))
        .andExpect(jsonPath("$.tools[0].runId").value("retained-home-run"))
        .andExpect(jsonPath("$.tools[0].durationMillis").value(nullValue()))
        .andExpect(jsonPath("$.tools[0].approvalStatus").value("succeeded"))
        .andExpect(jsonPath("$.tools[0].input").value(containsString("light.room")))
        .andExpect(jsonPath("$.tools[0].input").value(containsString("Other parameters omitted")))
        .andExpect(content().string(not(containsString("do-not-expose"))));
    mvc.perform(get(route).with(accounts.authenticatedAs(outsider)))
        .andExpect(status().isNotFound());

    calls.record(
        new Entry(
            Instant.now().toString(),
            "home-assistant",
            "home_assistant_execute_action",
            owner.id().toString(),
            false,
            "home",
            List.of(),
            true,
            false,
            "pending_approval",
            null,
            "{\"content\":{\"approvalRequestId\":\"" + request.requestId() + "\"}}",
            0,
            internal,
            "safe input",
            "retained-home-run"));
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(jsonPath("$.tools.length()").value(1));
  }

  @Test
  void retainedPendingEvidenceReflectsActualApprovalOutcomeWithoutExposingAnotherConversation()
      throws Exception {
    var owner = accounts.newActivated(AccountRole.MEMBER);
    var actor = new Actor(owner.id());
    var conversation = conversations.createWebConversation(actor);
    String internal = new ActorConversationId(actor, conversation).value();
    var provider = mock(ZalavaProvider.class);
    when(provider.descriptor())
        .thenReturn(
            new ProviderDescriptor(
                "evidence-provider",
                "module",
                "test",
                "Fixture",
                "Fixture",
                "1",
                null,
                List.of(),
                Map.of()));
    var request =
        approvals.create(
            provider,
            new ZalavaToolDescriptor("write", "Write", true),
            new InvocationContext(owner.id().toString(), false, Map.of("conversationId", internal)),
            JsonNodeFactory.instance.objectNode().put("name", "sample"));
    calls.record(
        new Entry(
            Instant.now().toString(),
            "evidence-provider",
            "write",
            owner.id().toString(),
            false,
            "write",
            List.of(),
            true,
            false,
            "pending_approval",
            "Awaiting permission",
            "{\"content\":{\"approvalRequestId\":\""
                + request.requestId()
                + "\",\"status\":\"pending_approval\"}}",
            0,
            internal));
    String route = "/chat/" + conversation.value() + "/evidence";
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tools[0].pendingApproval").value(true))
        .andExpect(jsonPath("$.tools[0].approvalStatus").value("pending"));
    approvals.allowUnscoped(request.requestId());
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(jsonPath("$.tools[0].pendingApproval").value(false))
        .andExpect(jsonPath("$.tools[0].approvalStatus").value("unknown"));
    approvals.recordExecutionOutcome(request.requestId(), false);
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(jsonPath("$.tools[0].approvalStatus").value("failed"));
    approvals.recordExecutionOutcome(request.requestId(), true);
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(jsonPath("$.tools[0].approvalStatus").value("succeeded"));
    var outsider = accounts.newActivated(AccountRole.ADMIN);
    mvc.perform(get(route).with(accounts.authenticatedAs(outsider)))
        .andExpect(status().isNotFound());
  }

  @Test
  void onlyAnOwnedConversationReturnsItsOwnRunAndToolEvidence() throws Exception {
    var owner = accounts.newActivated(AccountRole.MEMBER);
    var other = accounts.newActivated(AccountRole.ADMIN);
    var actor = new Actor(owner.id());
    var conversation = conversations.createWebConversation(actor);
    var foreignConversation = conversations.createWebConversation(new Actor(other.id()));
    String internal = new ActorConversationId(actor, conversation).value();
    Instant now = Instant.now();
    runs.record(
        new AgentRun(
            "owned-evidence",
            internal,
            AgentRun.PromptType.CONVERSATIONAL,
            "Owned question",
            1,
            0,
            1000,
            0,
            List.of(),
            now,
            now,
            0,
            AgentRun.Status.SUCCEEDED,
            "Owned result",
            null));
    runs.record(
        new AgentRun(
            "foreign-evidence",
            new ActorConversationId(new Actor(other.id()), foreignConversation).value(),
            AgentRun.PromptType.CONVERSATIONAL,
            "Foreign question",
            0,
            0,
            1000,
            0,
            List.of(),
            now,
            now,
            0,
            AgentRun.Status.SUCCEEDED,
            "Foreign result",
            null));
    calls.record(
        new Entry(
            now.toString(),
            "provider",
            "owned-tool",
            owner.id().toString(),
            false,
            "read",
            List.of(),
            false,
            true,
            null,
            null,
            "Owned tool result",
            1,
            internal));
    calls.record(
        new Entry(
            now.toString(),
            "provider",
            "wrong-actor-tool",
            other.id().toString(),
            false,
            "read",
            List.of(),
            false,
            true,
            null,
            null,
            "Foreign tool result",
            1,
            internal));
    String route = "/chat/" + conversation.value() + "/evidence";
    mvc.perform(get(route).with(accounts.authenticatedAs(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runs[0].id").value("owned-evidence"))
        .andExpect(jsonPath("$.tools[0].name").value("owned-tool"))
        .andExpect(jsonPath("$.tools.length()").value(1))
        .andExpect(content().string(not(containsString("Foreign"))));
    mvc.perform(get(route).with(accounts.authenticatedAs(other))).andExpect(status().isNotFound());
    assertThat(ConversationChannelContext.conversation()).isEmpty();
    ConversationChannelContext.call(
        internal,
        () -> {
          assertThat(ConversationChannelContext.conversation()).contains(internal);
          return null;
        });
    assertThat(ConversationChannelContext.conversation()).isEmpty();
  }

  @Test
  void missingAndMismatchedConfirmationCannotChangeTheExistingPasswordOrCreateAnAccount()
      throws Exception {
    var owner = accounts.newActivated(AccountRole.MEMBER);
    mvc.perform(
            post("/account/password")
                .with(accounts.authenticatedAs(owner))
                .param("currentPassword", "PermanentPassword-123")
                .param("replacementPassword", "NewPassword-1234"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Passwords do not match")));
    mvc.perform(
            post("/account/password")
                .with(accounts.authenticatedAs(owner))
                .param("currentPassword", "PermanentPassword-123")
                .param("replacementPassword", "NewPassword-1234")
                .param("passwordConfirmation", "DifferentPassword-1234"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Passwords do not match")));
    mvc.perform(
            post("/zalava/accounts/create")
                .param("loginName", "rejected-confirmation-account")
                .param("temporaryPassword", "TemporaryPassword-1234")
                .param("role", "MEMBER"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("error", "Passwords do not match"));
  }
}
