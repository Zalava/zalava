package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.chat.ChatChannel;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers the actor-scoped WebSocket flows left out of {@link ChatWebSocketHandlerTest}: first-use
 * conversation bootstrap, channel switching, conversation creation, blank/missing payloads, and
 * disconnect bookkeeping. All collaborators are mocks; no broker or provider is contacted.
 */
class ChatWebSocketHandlerActorFlowsTest {

  private final ChatChannel legacy = mock(ChatChannel.class);
  private final AuthenticatedActorResolver actors = mock(AuthenticatedActorResolver.class);
  private final ActorChatCommands commands = mock(ActorChatCommands.class);
  private final ActorChatQueries queries = mock(ActorChatQueries.class);
  private final WebSocketSession session = mock(WebSocketSession.class);
  private final Actor actor = new Actor(AccountId.newId());
  private final ConversationReference conversation = ConversationReference.newReference();
  private final ObjectMapper json = new ObjectMapper();

  private ChatWebSocketHandler handler() {
    return new ChatWebSocketHandler(
        legacy, json, actors, new ActorWebSocketSessions(), commands, queries);
  }

  @Test
  void bootstrapsAFirstConversationWhenTheActorHasNone() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.conversations(actor)).thenReturn(List.of());
    when(commands.createWebConversation(actor)).thenReturn(conversation);
    when(queries.history(actor, conversation)).thenReturn(List.of());
    var handler = handler();

    handler.afterConnectionEstablished(session);

    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(messages.capture());
    String payload = messages.getValue().getPayload();
    assertThat(payload)
        .contains("channel-selector")
        .contains("chat-messages")
        .contains("your SEA assistant")
        .contains("chat-input-area")
        .contains(conversation.value())
        .contains("chat-form")
        .contains("Message SEA...")
        .doesNotContain("read-only view");
    verify(legacy, never()).setWsSession(session);
    verify(legacy, never()).sendHtml(anyString());
  }

  @Test
  void selectsTheMostRecentConversationWhenTheActorAlreadyHasOne() throws Exception {
    ConversationReference older = ConversationReference.newReference();
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.conversations(actor)).thenReturn(List.of(older, conversation));
    when(queries.history(actor, conversation)).thenReturn(List.of());
    var handler = handler();

    handler.afterConnectionEstablished(session);

    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(messages.capture());
    assertThat(messages.getValue().getPayload()).contains(conversation.value());
    verify(commands, never()).createWebConversation(actor);
  }

  @Test
  void channelChangedRendersActorHistoryAndInputArea() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.history(actor, conversation)).thenReturn(List.of());
    var handler = handler();

    handler.handleTextMessage(
        session, message(Map.of("type", "channelChanged", "conversationId", conversation.value())));

    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(messages.capture());
    assertThat(messages.getValue().getPayload())
        .contains("chat-messages")
        .contains("chat-input-area")
        .contains("chat-form")
        .contains("Message SEA...")
        .doesNotContain("read-only view");
    verify(legacy, never()).sendHtml(anyString());
  }

  @Test
  void createConversationUsesActorScopedCommands() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(commands.createWebConversation(actor)).thenReturn(conversation);
    when(queries.conversations(actor)).thenReturn(List.of(conversation));
    when(queries.history(actor, conversation)).thenReturn(List.of());
    var handler = handler();

    handler.handleTextMessage(session, message(Map.of("type", "createConversation")));

    verify(commands).createWebConversation(actor);
    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(messages.capture());
    assertThat(messages.getValue().getPayload())
        .contains("channel-selector")
        .contains(conversation.value())
        .contains("Message SEA...")
        .doesNotContain("read-only view");
    verify(legacy, never()).createWebConversation();
  }

  @Test
  void userMessageWithoutAnActorConversationIdentifierIsRejected() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(actors.actorForLogin("member")).thenReturn(actor);
    var handler = handler();

    handler.handleTextMessage(
        session, message(Map.of("type", "userMessage", "conversationId", " ", "message", "hello")));

    verify(commands, never()).chat(actor, conversation, "hello");
    verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void blankUserMessagesAreIgnored() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(actors.actorForLogin("member")).thenReturn(actor);
    var handler = handler();

    handler.handleTextMessage(
        session,
        message(
            Map.of(
                "type", "userMessage", "conversationId", conversation.value(), "message", "   ")));

    verify(commands, never())
        .chat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString());
    verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void unknownMessageTypesAreIgnored() throws Exception {
    var handler = handler();

    handler.handleTextMessage(session, message(Map.of("type", "somethingElse")));

    verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    verifyNoLegacyInteractions();
  }

  @Test
  void blankChannelSwitchesAreIgnored() throws Exception {
    var handler = handler();

    handler.handleTextMessage(
        session, message(Map.of("type", "channelChanged", "conversationId", "")));

    verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    verifyNoLegacyInteractions();
  }

  @Test
  void actorPathSurfacesChatFailuresAsAgentBubbles() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              throw new IllegalStateException("provider offline");
            })
        .when(commands)
        .streamChat(
            org.mockito.ArgumentMatchers.eq(actor),
            org.mockito.ArgumentMatchers.eq(conversation),
            org.mockito.ArgumentMatchers.eq("hello"),
            org.mockito.ArgumentMatchers.any());
    var handler = handler();

    handler.handleTextMessage(
        session,
        message(
            Map.of(
                "type",
                "userMessage",
                "conversationId",
                conversation.value(),
                "message",
                "hello")));

    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
    assertThat(messages.getAllValues().get(0).getPayload())
        .contains("hello")
        .contains("typing-indicator")
        .contains("ar-typing");
    assertThat(messages.getAllValues().get(1).getPayload())
        .contains("An error occurred while contacting the AI provider")
        .contains("provider offline")
        .contains("typing-indicator")
        .doesNotContain("ar-typing");
  }

  @Test
  void actorFailurePathClearsTheTypingIndicatorEvenWhenHistoryRenderingFails() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              throw new RuntimeException((String) null);
            })
        .when(commands)
        .streamChat(
            org.mockito.ArgumentMatchers.eq(actor),
            org.mockito.ArgumentMatchers.eq(conversation),
            org.mockito.ArgumentMatchers.eq("hello"),
            org.mockito.ArgumentMatchers.any());
    var handler = handler();

    handler.handleTextMessage(
        session,
        message(
            Map.of(
                "type",
                "userMessage",
                "conversationId",
                conversation.value(),
                "message",
                "hello")));

    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
    assertThat(messages.getAllValues().get(1).getPayload())
        .contains("An error occurred while contacting the AI provider")
        .contains("RuntimeException");
  }

  @Test
  void disconnectRemovesTheActorSessionAndNotTheLegacyChannel() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    var handler = handler();

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(legacy, never()).clearWsSession(session);
  }

  @Test
  void legacyPathStillClearsTheChannelOnDisconnect() throws Exception {
    var handler = new ChatWebSocketHandler(legacy, json);

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(legacy).clearWsSession(session);
  }

  @Test
  void anUnauthenticatedSessionIsRejected() {
    when(session.getPrincipal()).thenReturn(null);
    var handler = handler();

    assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void actorHistoryRendersUserAndAgentBubblesButFiltersSystemMessages() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.history(actor, conversation))
        .thenReturn(
            List.of(
                new org.zalava.assistant.chat.domain.ChatMessage(
                    org.zalava.assistant.chat.domain.ChatMessage.Role.SYSTEM, "hidden system note"),
                new org.zalava.assistant.chat.domain.ChatMessage(
                    org.zalava.assistant.chat.domain.ChatMessage.Role.USER, "from the user"),
                new org.zalava.assistant.chat.domain.ChatMessage(
                    org.zalava.assistant.chat.domain.ChatMessage.Role.ASSISTANT,
                    "from the agent")));
    var handler = handler();

    handler.handleTextMessage(
        session, message(Map.of("type", "channelChanged", "conversationId", conversation.value())));

    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(messages.capture());
    String payload = messages.getValue().getPayload();
    assertThat(payload).contains("from the user").contains("from the agent");
    assertThat(payload).doesNotContain("hidden system note");
  }

  @Test
  void userMessageStreamsDeltasIntoAStreamingBubbleAndFinalizesWithJobLinks() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    var jobReference = org.zalava.tasks.domain.ActorTaskReference.newReference();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              org.zalava.assistant.chat.application.port.in.ActorChatStreamListener listener =
                  invocation.getArgument(3);
              listener.onDelta("Hel");
              listener.onDelta("lo");
              listener.onComplete("Hello", List.of(jobReference));
              return new org.zalava.assistant.chat.domain.ActorChatTurn(
                  "Hello", List.of(jobReference));
            })
        .when(commands)
        .streamChat(
            org.mockito.ArgumentMatchers.eq(actor),
            org.mockito.ArgumentMatchers.eq(conversation),
            org.mockito.ArgumentMatchers.eq("hello"),
            org.mockito.ArgumentMatchers.any());
    var handler = handler();

    handler.handleTextMessage(
        session,
        message(
            Map.of(
                "type",
                "userMessage",
                "conversationId",
                conversation.value(),
                "message",
                "hello")));

    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.atLeast(3)).sendMessage(messages.capture());
    List<String> payloads = messages.getAllValues().stream().map(TextMessage::getPayload).toList();
    // Echo of the user message, then the streaming bubble on the first delta.
    assertThat(payloads.get(0)).contains("hello").contains("typing-indicator");
    assertThat(payloads.get(1))
        .contains("streaming-bubble")
        .contains("Hel")
        .doesNotContain("read-only view");
    // Subsequent deltas replace the streaming bubble's inner HTML.
    assertThat(payloads.get(2)).contains("streaming-bubble").contains("Hello");
    // Completion swaps the streaming bubble for the final bubble with job links.
    String finalPayload = payloads.getLast();
    assertThat(finalPayload)
        .contains("ar-msg--agent")
        .contains("Hello")
        .contains("hx-swap-oob=\"delete\"")
        .contains("View job")
        .contains("typing-indicator")
        .doesNotContain("ar-typing");
  }

  @Test
  void userMessageStreamFailureSurfacesTheErrorBubbleAndClearsTheIndicator() throws Exception {
    when(session.getPrincipal()).thenReturn(principal("member"));
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              org.zalava.assistant.chat.application.port.in.ActorChatStreamListener listener =
                  invocation.getArgument(3);
              listener.onDelta("par");
              listener.onError(new IllegalStateException("provider offline"));
              throw new IllegalStateException("provider offline");
            })
        .when(commands)
        .streamChat(
            org.mockito.ArgumentMatchers.eq(actor),
            org.mockito.ArgumentMatchers.eq(conversation),
            org.mockito.ArgumentMatchers.eq("hello"),
            org.mockito.ArgumentMatchers.any());
    var handler = handler();

    handler.handleTextMessage(
        session,
        message(
            Map.of(
                "type",
                "userMessage",
                "conversationId",
                conversation.value(),
                "message",
                "hello")));

    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.atLeast(3)).sendMessage(messages.capture());
    List<String> payloads = messages.getAllValues().stream().map(TextMessage::getPayload).toList();
    assertThat(payloads.getLast())
        .contains("An error occurred while contacting the AI provider")
        .contains("provider offline")
        .contains("typing-indicator")
        .doesNotContain("ar-typing");
  }

  private TextMessage message(Map<String, Object> payload) throws IOException {
    return new TextMessage(json.writeValueAsString(payload));
  }

  private static Principal principal(String name) {
    return () -> name;
  }

  private void verifyNoLegacyInteractions() throws IOException {
    verify(legacy, never()).sendHtml(anyString());
    verify(legacy, never()).setWsSession(session);
    verify(legacy, never()).clearWsSession(session);
  }
}
