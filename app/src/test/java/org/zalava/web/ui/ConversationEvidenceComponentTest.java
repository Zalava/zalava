package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.assistant.agent.ConversationChannelContext;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.support.AuthenticatedZalavaComponentTest;
import org.zalava.support.ComponentTestAccounts;
import org.zalava.web.control.application.InvocationLog;
import org.zalava.web.control.application.port.in.InvocationLogQueries.Entry;

@AuthenticatedZalavaComponentTest
class ConversationEvidenceComponentTest {
  @Autowired MockMvc mvc;
  @Autowired ComponentTestAccounts accounts;
  @Autowired ActorChatCommands conversations;
  @Autowired AgentRunStore runs;
  @Autowired InvocationLog calls;

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
