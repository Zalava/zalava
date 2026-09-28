package org.zalava.chat.application;

import java.util.List;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.application.port.out.AccountStore;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.application.port.in.AgentExecution;
import org.zalava.chat.application.port.in.ActorChatCommands;
import org.zalava.chat.application.port.in.ActorChatQueries;
import org.zalava.chat.domain.ActorChatTurn;
import org.zalava.chat.domain.ChatMessage;
import org.zalava.conversation.application.port.in.ActorConversations;
import org.zalava.conversation.domain.ActorConversationId;
import org.zalava.conversation.domain.ConversationReference;
import org.zalava.tools.ActorTaskCreationContext;

/** Actor-bound product chat orchestration over the owner-scoped conversation port. */
public final class ActorChatUseCases implements ActorChatCommands, ActorChatQueries {
  private final AgentExecution agent;
  private final ActorConversations conversations;
  private final ActorTaskCreationContext taskCreation;
  private final ActorExecutionContext actorExecution;
  private final AccountStore accounts;

  public ActorChatUseCases(
      AgentExecution agent,
      ActorConversations conversations,
      ActorTaskCreationContext taskCreation,
      ActorExecutionContext actorExecution,
      AccountStore accounts) {
    this.agent = agent;
    this.conversations = conversations;
    this.taskCreation = taskCreation;
    this.actorExecution = actorExecution;
    this.accounts = accounts;
  }

  @Override
  public ActorChatTurn chat(Actor actor, ConversationReference conversation, String message) {
    requireOwned(actor, conversation);
    var account =
        accounts
            .findById(actor.accountId())
            .filter(value -> value.enabled())
            .orElseThrow(() -> new IllegalStateException("Actor account is unavailable"));
    var capture =
        taskCreation.capture(
            () ->
                actorExecution.call(
                    actor,
                    account.role(),
                    () ->
                        agent.respondTo(
                            new ActorConversationId(actor, conversation).value(), message)));
    return new ActorChatTurn(capture.value(), capture.taskReferences());
  }

  @Override
  public ActorChatTurn streamChat(
      Actor actor,
      ConversationReference conversation,
      String message,
      org.zalava.chat.application.port.in.ActorChatStreamListener listener) {
    requireOwned(actor, conversation);
    var account =
        accounts
            .findById(actor.accountId())
            .filter(value -> value.enabled())
            .orElseThrow(() -> new IllegalStateException("Actor account is unavailable"));
    var capture =
        taskCreation.capture(
            () -> {
              try {
                return actorExecution.call(
                    actor,
                    account.role(),
                    () ->
                        agent.respondTo(
                            new ActorConversationId(actor, conversation).value(),
                            message,
                            new org.zalava.agent.application.port.in.AgentStreamListener() {
                              @Override
                              public void onDelta(String text) {
                                listener.onDelta(text);
                              }

                              @Override
                              public void onComplete(String fullText) {
                                // Completion is issued by streamChat after the capture returns,
                                // where the collected job references are available.
                              }

                              @Override
                              public void onError(RuntimeException failure) {
                                // Failure is reported by streamChat once the capture unwinds.
                              }
                            }));
              } catch (RuntimeException failure) {
                listener.onError(failure);
                throw failure;
              }
            });
    listener.onComplete(capture.value(), capture.taskReferences());
    return new ActorChatTurn(capture.value(), capture.taskReferences());
  }

  @Override
  public ConversationReference createWebConversation(Actor actor) {
    ConversationReference reference = ConversationReference.newReference();
    conversations.saveAll(actor, reference, List.of());
    return reference;
  }

  @Override
  public List<ConversationReference> conversations(Actor actor) {
    return conversations.findReferences(actor);
  }

  @Override
  public List<ChatMessage> history(Actor actor, ConversationReference conversation) {
    requireOwned(actor, conversation);
    return conversations.findByReference(actor, conversation).stream()
        .map(
            message ->
                new ChatMessage(ChatMessage.Role.valueOf(message.role().name()), message.text()))
        .toList();
  }

  private void requireOwned(Actor actor, ConversationReference conversation) {
    if (!conversations.findReferences(actor).contains(conversation)) {
      throw new IllegalArgumentException("Conversation not found");
    }
  }
}
