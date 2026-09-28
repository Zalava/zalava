package org.zalava.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.application.port.out.AccountStore;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.application.port.in.AgentExecution;
import org.zalava.agent.application.port.in.AgentStreamListener;
import org.zalava.chat.application.port.in.ActorChatStreamListener;
import org.zalava.chat.domain.ActorChatTurn;
import org.zalava.conversation.application.port.in.ActorConversations;
import org.zalava.conversation.domain.ConversationReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tools.ActorTaskCreationContext;

/**
 * Covers the streamed chat use case: live delta forwarding, role propagation into the agent call,
 * job-reference capture across the turn, and completion carrying the final text and references.
 */
class ActorChatUseCasesStreamingTest {

  @Test
  void forwardsDeltasLiveAndCompletesWithTheFullTextAndCapturedReferences() {
    Actor owner = new Actor(AccountId.newId());
    ConversationReference conversation = ConversationReference.newReference();
    ActorTaskReference job = ActorTaskReference.newReference();
    ActorConversations conversations = mock(ActorConversations.class);
    AgentExecution agent = mock(AgentExecution.class);
    AccountStore accounts = mock(AccountStore.class);
    ActorExecutionContext execution = new ActorExecutionContext();
    ActorTaskCreationContext taskCreation = new ActorTaskCreationContext();
    when(conversations.findReferences(owner)).thenReturn(List.of(conversation));
    when(accounts.findById(owner.accountId())).thenReturn(Optional.of(account(owner)));
    when(agent.respondTo(eq(owner.accountId() + ":" + conversation.value()), eq("hi"), any()))
        .thenAnswer(
            invocation -> {
              AgentStreamListener listener = invocation.getArgument(2);
              listener.onDelta("Hel");
              listener.onDelta("lo ");
              taskCreation.taskCreated(job);
              listener.onDelta("world");
              listener.onComplete("Hello world");
              return "Hello world";
            });
    var useCases = new ActorChatUseCases(agent, conversations, taskCreation, execution, accounts);

    List<String> deltas = new ArrayList<>();
    List<ActorTaskReference> completionReferences = new ArrayList<>();
    List<String> completionText = new ArrayList<>();
    ActorChatTurn turn =
        useCases.streamChat(
            owner,
            conversation,
            "hi",
            new ActorChatStreamListener() {
              @Override
              public void onDelta(String text) {
                deltas.add(text);
              }

              @Override
              public void onComplete(String fullText, List<ActorTaskReference> jobReferences) {
                completionText.add(fullText);
                completionReferences.addAll(jobReferences);
              }

              @Override
              public void onError(RuntimeException failure) {
                throw failure;
              }
            });

    assertThat(deltas).containsExactly("Hel", "lo ", "world").doesNotContain("Hello world");
    assertThat(completionText).containsExactly("Hello world");
    assertThat(completionReferences).containsExactly(job);
    assertThat(turn.text()).isEqualTo("Hello world");
    assertThat(turn.taskReferences()).containsExactly(job);
    assertThat(execution.currentPrincipal()).isEmpty();
  }

  @Test
  void streamsUnderThePersistedAccountRole() {
    Actor owner = new Actor(AccountId.newId());
    ConversationReference conversation = ConversationReference.newReference();
    ActorConversations conversations = mock(ActorConversations.class);
    AgentExecution agent = mock(AgentExecution.class);
    AccountStore accounts = mock(AccountStore.class);
    ActorExecutionContext execution = new ActorExecutionContext();
    when(conversations.findReferences(owner)).thenReturn(List.of(conversation));
    when(accounts.findById(owner.accountId()))
        .thenReturn(
            Optional.of(
                new Account(
                    owner.accountId(),
                    "admin",
                    "hash",
                    true,
                    AccountRole.ADMIN,
                    false,
                    Instant.parse("2026-08-27T00:00:00Z"),
                    Instant.parse("2026-08-27T00:00:00Z"),
                    0)));
    List<AccountRole> rolesDuringStream = new ArrayList<>();
    when(agent.respondTo(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              rolesDuringStream.add(execution.currentPrincipal().orElseThrow().role());
              return "ok";
            });
    var useCases = new ActorChatUseCases(agent, conversations, taskCreation(), execution, accounts);

    useCases.streamChat(owner, conversation, "hi", noopListener());

    assertThat(rolesDuringStream).containsExactly(AccountRole.ADMIN);
  }

  @Test
  void aFailedStreamNotifiesTheListenerErrorAndRethrows() {
    Actor owner = new Actor(AccountId.newId());
    ConversationReference conversation = ConversationReference.newReference();
    ActorConversations conversations = mock(ActorConversations.class);
    AgentExecution agent = mock(AgentExecution.class);
    AccountStore accounts = mock(AccountStore.class);
    when(conversations.findReferences(owner)).thenReturn(List.of(conversation));
    when(accounts.findById(owner.accountId())).thenReturn(Optional.of(account(owner)));
    RuntimeException failure = new IllegalStateException("provider offline");
    when(agent.respondTo(any(), any(), any())).thenThrow(failure);
    var useCases =
        new ActorChatUseCases(
            agent,
            conversations,
            new ActorTaskCreationContext(),
            new ActorExecutionContext(),
            accounts);

    List<RuntimeException> errors = new ArrayList<>();
    assertThatThrownBy(() -> useCases.streamChat(owner, conversation, "hi", noopListener(errors)))
        .isSameAs(failure);
    assertThat(errors).containsExactly(failure);
  }

  private static ActorTaskCreationContext taskCreation() {
    return new ActorTaskCreationContext();
  }

  private static ActorChatStreamListener noopListener() {
    return new ActorChatStreamListener() {
      @Override
      public void onDelta(String text) {}

      @Override
      public void onComplete(String fullText, List<ActorTaskReference> jobReferences) {}

      @Override
      public void onError(RuntimeException failure) {
        throw failure;
      }
    };
  }

  private static ActorChatStreamListener noopListener(List<RuntimeException> errors) {
    return new ActorChatStreamListener() {
      @Override
      public void onDelta(String text) {}

      @Override
      public void onComplete(String fullText, List<ActorTaskReference> jobReferences) {}

      @Override
      public void onError(RuntimeException failure) {
        errors.add(failure);
      }
    };
  }

  private static Account account(Actor actor) {
    Instant now = Instant.parse("2026-08-27T00:00:00Z");
    return new Account(
        actor.accountId(), "member", "hash", true, AccountRole.MEMBER, false, now, now, 0);
  }
}
