package org.zalava.conversation.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.conversation.application.port.in.ActorConversations;
import org.zalava.conversation.application.port.in.ConversationRepository;
import org.zalava.conversation.domain.ActorConversationId;
import org.zalava.conversation.domain.ConversationMessage;
import org.zalava.conversation.domain.ConversationReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

class SpringAiChatMemoryRepositoryTest {
  @Test
  void mapsSpringAiMessagesAtTheCompatibilityEdge() {
    ConversationRepository conversations = mock(ConversationRepository.class);
    when(conversations.findByConversationId("web"))
        .thenReturn(List.of(new ConversationMessage(ConversationMessage.Role.USER, "hello")));
    var adapter = new SpringAiChatMemoryRepository(conversations);
    assertThat(adapter.findByConversationId("web")).singleElement().isInstanceOf(UserMessage.class);
    adapter.appendAll("web", List.of(new UserMessage("again")));
    verify(conversations)
        .appendAll("web", List.of(new ConversationMessage(ConversationMessage.Role.USER, "again")));
  }

  @Test
  void routesActorConversationMemoryOnlyThroughTheActorScopedPort() {
    ConversationRepository legacy = mock(ConversationRepository.class);
    ActorConversations actorConversations = mock(ActorConversations.class);
    Actor actor = new Actor(AccountId.newId());
    ConversationReference reference = ConversationReference.newReference();
    String id = new ActorConversationId(actor, reference).value();
    when(actorConversations.findByReference(actor, reference))
        .thenReturn(List.of(new ConversationMessage(ConversationMessage.Role.USER, "private")));
    var adapter = new SpringAiChatMemoryRepository(legacy, actorConversations);

    assertThat(adapter.findByConversationId(id)).singleElement().isInstanceOf(UserMessage.class);
    adapter.appendAll(id, List.of(new UserMessage("again")));

    verify(actorConversations)
        .appendAll(
            actor,
            reference,
            List.of(new ConversationMessage(ConversationMessage.Role.USER, "again")));
    verifyNoInteractions(legacy);
  }
}
