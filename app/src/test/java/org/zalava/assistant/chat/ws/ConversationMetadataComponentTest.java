package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.support.SecureZalavaComponentTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SecureZalavaComponentTest
@TestPropertySource(
    properties = {"zalava.test.chat-provider=openai", "zalava.test.context=conversation-metadata"})
class ConversationMetadataComponentTest {
  @Autowired UiChatWebSocketHandler websocket;
  @Autowired ActorChatCommands commands;
  @Autowired ActorChatQueries queries;
  @Autowired AccountLifecycle accounts;
  @Autowired ObjectMapper json;

  @Test
  void listHasAReadableQuestionTitleAndActualPersistedDates() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ConversationReference conversation = commands.createWebConversation(actor);
    commands.chat(actor, conversation, "Plan the school trip for Tuesday.");
    WebSocketSession session = session(owner);
    websocket.afterConnectionEstablished(session);
    JsonNode entry = summary(session, conversation);
    assertThat(entry.path("title").asString())
        .contains("school trip")
        .doesNotContain(conversation.value());
    Instant created = Instant.parse(entry.path("createdAt").asString());
    Instant updated = Instant.parse(entry.path("updatedAt").asString());
    assertThat(updated).isAfterOrEqualTo(created);
  }

  @Test
  void renameSurvivesFurtherMessagesAndReconnectWithoutChangingHistory() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ConversationReference conversation = commands.createWebConversation(actor);
    commands.chat(actor, conversation, "Plan the school trip for Tuesday.");
    WebSocketSession session = session(owner);
    websocket.handleTextMessage(session, rename(conversation, "School trip planning"));
    commands.chat(actor, conversation, "Add a packing checklist.");
    WebSocketSession reconnect = session(owner);
    websocket.afterConnectionEstablished(reconnect);
    assertThat(summary(reconnect, conversation).path("title").asString())
        .isEqualTo("School trip planning");
    assertThat(queries.history(actor, conversation))
        .filteredOn(m -> m.role() == ChatMessage.Role.USER)
        .extracting(ChatMessage::text)
        .containsExactly("Plan the school trip for Tuesday.", "Add a packing checklist.");
  }

  @Test
  void anotherActorCannotListOrRenameTheOwnersConversation() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ConversationReference conversation = commands.createWebConversation(actor);
    commands.chat(actor, conversation, "Private school trip planning.");
    WebSocketSession outsider = session(member());
    websocket.afterConnectionEstablished(outsider);
    websocket.handleTextMessage(outsider, rename(conversation, "Stolen title"));
    assertThat(payloads(outsider))
        .contains("failure")
        .doesNotContain("Private school trip planning.", conversation.value());
    WebSocketSession reconnect = session(owner);
    websocket.afterConnectionEstablished(reconnect);
    assertThat(summary(reconnect, conversation).path("title").asString())
        .contains("Private school trip")
        .doesNotContain("Stolen");
  }

  @Test
  void invalidTitleIsRejectedAndPreservesTheSavedTitle() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    ConversationReference conversation = commands.createWebConversation(actor);
    commands.chat(actor, conversation, "Keep this title.");
    WebSocketSession session = session(owner);
    websocket.handleTextMessage(session, rename(conversation, " "));
    websocket.handleTextMessage(session, rename(conversation, "x".repeat(121)));
    assertThat(payloads(session)).contains("failure");
    WebSocketSession reconnect = session(owner);
    websocket.afterConnectionEstablished(reconnect);
    assertThat(summary(reconnect, conversation).path("title").asString())
        .isEqualTo("Keep this title.");
  }

  @Test
  void firstConnectionListsTheNewConversationBeforeSelectingIt() throws Exception {
    WebSocketSession session = session(member());
    websocket.afterConnectionEstablished(session);
    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(messages.capture());
    JsonNode list = json.readTree(messages.getAllValues().getFirst().getPayload());
    assertThat(list.path("type").asString()).isEqualTo("conversation.list");
    assertThat(list.path("conversationIds").size()).isEqualTo(1);
    assertThat(list.path("conversations").get(0).path("title").asString())
        .isEqualTo("New conversation");
  }

  private JsonNode summary(WebSocketSession session, ConversationReference conversation)
      throws Exception {
    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(messages.capture());
    for (TextMessage message : messages.getAllValues()) {
      JsonNode event = json.readTree(message.getPayload());
      if (event.path("type").asString().equals("conversation.list"))
        for (JsonNode entry : event.path("conversations"))
          if (entry.path("id").asString().equals(conversation.value())) return entry;
    }
    throw new AssertionError("No conversation summary was delivered for the owned record");
  }

  private TextMessage rename(ConversationReference conversation, String title) throws Exception {
    return new TextMessage(
        json.writeValueAsString(
            Map.of(
                "protocol",
                "zalava.ui/v1",
                "type",
                "chat.rename",
                "conversationId",
                conversation.value(),
                "title",
                title)));
  }

  private static String payloads(WebSocketSession session) throws Exception {
    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(messages.capture());
    return messages.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat);
  }

  private static WebSocketSession session(Account account) {
    var session = mock(WebSocketSession.class);
    when(session.getPrincipal()).thenReturn((Principal) () -> account.loginName());
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private Account member() {
    Account account =
        accounts.create(
            "conversation-meta-" + UUID.randomUUID(),
            "ConversationPassword-123",
            AccountRole.MEMBER);
    accounts.changePassword(
        account.id(), "ConversationPassword-123", "ChangedConversationPassword-123");
    return account;
  }
}
