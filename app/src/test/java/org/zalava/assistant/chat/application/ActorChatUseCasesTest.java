package org.zalava.assistant.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.assistant.agent.application.port.in.AgentExecution;
import org.zalava.assistant.conversation.application.port.in.ActorConversations;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.out.AccountStore;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.capture.ActorTaskCreationContext;
import org.zalava.tasks.domain.ActorTaskReference;

class ActorChatUseCasesTest {
  @Test
  void runsChatWithThePersistedActorRoleAndCapturesOnlyActorTaskReferences() {
    Actor owner = new Actor(AccountId.newId());
    ConversationReference conversation = ConversationReference.newReference();
    ActorTaskReference task = ActorTaskReference.newReference();
    ActorConversations conversations = mock(ActorConversations.class);
    AgentExecution agent = mock(AgentExecution.class);
    AccountStore accounts = mock(AccountStore.class);
    ActorExecutionContext execution = new ActorExecutionContext();
    ActorTaskCreationContext taskCreation = new ActorTaskCreationContext();
    when(conversations.findReferences(owner)).thenReturn(List.of(conversation));
    when(accounts.findById(owner.accountId())).thenReturn(Optional.of(account(owner)));
    when(agent.respondTo(owner.accountId() + ":" + conversation.value(), "create a job"))
        .thenAnswer(
            ignored -> {
              assertThat(execution.currentPrincipal().orElseThrow().actor()).isEqualTo(owner);
              assertThat(execution.currentPrincipal().orElseThrow().role())
                  .isEqualTo(AccountRole.MEMBER);
              taskCreation.taskCreated(task);
              return "created";
            });
    var useCases = new ActorChatUseCases(agent, conversations, taskCreation, execution, accounts);

    var turn = useCases.chat(owner, conversation, "create a job");

    assertThat(turn.text()).isEqualTo("created");
    assertThat(turn.taskReferences()).containsExactly(task);
    assertThat(execution.currentPrincipal()).isEmpty();
  }

  @Test
  void refusesAConversationReferenceThatIsNotOwnedByTheActor() {
    Actor owner = new Actor(AccountId.newId());
    Actor other = new Actor(AccountId.newId());
    ConversationReference conversation = ConversationReference.newReference();
    ActorConversations conversations = mock(ActorConversations.class);
    AgentExecution agent = mock(AgentExecution.class);
    when(conversations.findReferences(owner)).thenReturn(List.of(conversation));
    when(conversations.findReferences(other)).thenReturn(List.of());
    var useCases =
        new ActorChatUseCases(
            agent,
            conversations,
            new ActorTaskCreationContext(),
            new ActorExecutionContext(),
            mock(AccountStore.class));

    assertThatThrownBy(() -> useCases.history(other, conversation))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Conversation not found");
    assertThatThrownBy(() -> useCases.chat(other, conversation, "steal history"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Conversation not found");
    verifyNoInteractions(agent);
  }

  private static Account account(Actor actor) {
    Instant now = Instant.parse("2026-08-27T00:00:00Z");
    return new Account(
        actor.accountId(), "member", "hash", true, AccountRole.MEMBER, false, now, now, 0);
  }
}
