package org.zalava.assistant.conversation.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.zalava.assistant.conversation.application.port.in.ActorConversations;
import org.zalava.assistant.conversation.application.port.in.ConversationRepository;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;

/**
 * Covers the Spring AI compatibility-edge branches: role mapping for every supported message type,
 * unsupported-type rejection, blank-message filtering, and the actor/legacy save-delete routing
 * split.
 */
class SpringAiChatMemoryRepositoryBranchesTest {

  @Test
  void mapsEverySupportedRoleWhenReadingHistory() {
    ConversationRepository conversations = mock(ConversationRepository.class);
    when(conversations.findByConversationId("web"))
        .thenReturn(
            List.of(
                new ConversationMessage(ConversationMessage.Role.USER, "u"),
                new ConversationMessage(ConversationMessage.Role.ASSISTANT, "a"),
                new ConversationMessage(ConversationMessage.Role.SYSTEM, "s")));
    var adapter = new SpringAiChatMemoryRepository(conversations);

    List<org.springframework.ai.chat.messages.Message> mapped = adapter.findByConversationId("web");

    assertThat(mapped.get(0)).isInstanceOf(UserMessage.class);
    assertThat(mapped.get(1)).isInstanceOf(AssistantMessage.class);
    assertThat(mapped.get(2)).isInstanceOf(SystemMessage.class);
  }

  @Test
  void toolResponseMessagesCarryNoPersistableTextAndAreDroppedByTheBlankFilter() {
    ConversationRepository legacy = mock(ConversationRepository.class);
    var adapter = new SpringAiChatMemoryRepository(legacy);
    var toolMessage =
        ToolResponseMessage.builder()
            .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "tool", "{}")))
            .build();

    adapter.appendAll("web", List.of(toolMessage));

    verify(legacy).appendAll("web", List.of());
  }

  @Test
  void saveRoutesActorScopedAndLegacyConversationSeparately() {
    ConversationRepository legacy = mock(ConversationRepository.class);
    ActorConversations actorConversations = mock(ActorConversations.class);
    Actor actor = new Actor(AccountId.newId());
    ConversationReference reference = ConversationReference.newReference();
    String actorId = new ActorConversationId(actor, reference).value();
    var adapter = new SpringAiChatMemoryRepository(legacy, actorConversations);

    adapter.saveAll(actorId, List.of(new UserMessage("private save")));
    adapter.saveAll("web", List.of(new UserMessage("legacy save")));

    verify(actorConversations)
        .saveAll(
            actor,
            reference,
            List.of(new ConversationMessage(ConversationMessage.Role.USER, "private save")));
    verify(legacy)
        .saveAll(
            "web", List.of(new ConversationMessage(ConversationMessage.Role.USER, "legacy save")));
  }

  @Test
  void deleteRoutesActorScopedAndLegacyConversationSeparately() {
    ConversationRepository legacy = mock(ConversationRepository.class);
    ActorConversations actorConversations = mock(ActorConversations.class);
    Actor actor = new Actor(AccountId.newId());
    ConversationReference reference = ConversationReference.newReference();
    String actorId = new ActorConversationId(actor, reference).value();
    var adapter = new SpringAiChatMemoryRepository(legacy, actorConversations);

    adapter.deleteByConversationId(actorId);
    adapter.deleteByConversationId("web");

    verify(actorConversations).delete(actor, reference);
    verify(legacy).deleteByConversationId("web");
  }

  @Test
  void blankMessagesAreDroppedBeforePersistence() {
    ConversationRepository legacy = mock(ConversationRepository.class);
    var adapter = new SpringAiChatMemoryRepository(legacy);

    adapter.appendAll("web", List.of(new UserMessage("  "), new AssistantMessage("kept")));

    verify(legacy)
        .appendAll(
            "web", List.of(new ConversationMessage(ConversationMessage.Role.ASSISTANT, "kept")));
  }

  @Test
  void anInvalidActorConversationIdentifierFallsBackToTheLegacyRepository() {
    ConversationRepository legacy = mock(ConversationRepository.class);
    ActorConversations actorConversations = mock(ActorConversations.class);
    var adapter = new SpringAiChatMemoryRepository(legacy, actorConversations);

    adapter.appendAll("not-an-actor-conversation", List.of(new UserMessage("hi")));

    verify(legacy)
        .appendAll(
            "not-an-actor-conversation",
            List.of(new ConversationMessage(ConversationMessage.Role.USER, "hi")));
  }
}
