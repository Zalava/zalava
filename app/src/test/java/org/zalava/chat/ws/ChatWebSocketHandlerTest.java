package org.zalava.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.chat.ChatChannel;
import org.zalava.chat.ChatTurnResult;
import org.zalava.chat.application.port.in.ActorChatCommands;
import org.zalava.chat.application.port.in.ActorChatQueries;
import org.zalava.chat.domain.ActorChatTurn;
import org.zalava.conversation.domain.ConversationReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class ChatWebSocketHandlerTest {

  @Test
  void bindsConversationSelectionAndChatToTheAuthenticatedActor() throws Exception {
    ChatChannel legacy = mock(ChatChannel.class);
    AuthenticatedActorResolver actors = mock(AuthenticatedActorResolver.class);
    ActorChatCommands commands = mock(ActorChatCommands.class);
    ActorChatQueries queries = mock(ActorChatQueries.class);
    WebSocketSession session = mock(WebSocketSession.class);
    Actor actor = new Actor(AccountId.newId());
    ConversationReference conversation = ConversationReference.newReference();
    ActorTaskReference task = ActorTaskReference.newReference();
    when(session.getPrincipal()).thenReturn((Principal) () -> "member");
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.conversations(actor)).thenReturn(List.of(conversation));
    when(queries.history(actor, conversation)).thenReturn(List.of());
    when(commands.streamChat(
            org.mockito.ArgumentMatchers.eq(actor),
            org.mockito.ArgumentMatchers.eq(conversation),
            org.mockito.ArgumentMatchers.eq("hello"),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              org.zalava.chat.application.port.in.ActorChatStreamListener listener =
                  invocation.getArgument(3);
              listener.onDelta("private ");
              listener.onComplete("private answer", List.of(task));
              return new ActorChatTurn("private answer", List.of(task));
            });
    var handler =
        new ChatWebSocketHandler(
            legacy, new ObjectMapper(), actors, new ActorWebSocketSessions(), commands, queries);

    handler.afterConnectionEstablished(session);
    handler.handleTextMessage(
        session,
        new TextMessage(
            new ObjectMapper()
                .writeValueAsString(
                    Map.of(
                        "type", "userMessage",
                        "conversationId", conversation.value(),
                        "message", "hello"))));

    verify(commands)
        .streamChat(
            org.mockito.ArgumentMatchers.eq(actor),
            org.mockito.ArgumentMatchers.eq(conversation),
            org.mockito.ArgumentMatchers.eq("hello"),
            org.mockito.ArgumentMatchers.any());
    verifyNoMoreInteractions(legacy);
    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.times(4)).sendMessage(messages.capture());
    assertThat(messages.getAllValues().get(0).getPayload()).contains(conversation.value());
    assertThat(messages.getAllValues().get(2).getPayload()).contains("private ");
    assertThat(messages.getAllValues().get(3).getPayload())
        .contains("private answer")
        .contains("/jobs/" + task.value());
  }

  @Test
  void refusesClientSelectedConversationNotOwnedByThePrincipal() throws Exception {
    AuthenticatedActorResolver actors = mock(AuthenticatedActorResolver.class);
    ActorChatCommands commands = mock(ActorChatCommands.class);
    ActorChatQueries queries = mock(ActorChatQueries.class);
    WebSocketSession session = mock(WebSocketSession.class);
    Actor actor = new Actor(AccountId.newId());
    ConversationReference otherConversation = ConversationReference.newReference();
    when(session.getPrincipal()).thenReturn((Principal) () -> "member");
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.history(actor, otherConversation))
        .thenThrow(new IllegalArgumentException("Conversation not found"));
    var handler =
        new ChatWebSocketHandler(
            mock(ChatChannel.class),
            new ObjectMapper(),
            actors,
            new ActorWebSocketSessions(),
            commands,
            queries);

    TextMessage payload =
        new TextMessage(
            new ObjectMapper()
                .writeValueAsString(
                    Map.of("type", "channelChanged", "conversationId", otherConversation.value())));

    assertThatThrownBy(() -> handler.handleTextMessage(session, payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Conversation not found");
  }

  @Test
  void handleUserMessageShowsNonTransientErrorAndClearsTypingIndicatorWhenAgentFails()
      throws Exception {
    ChatChannel chatChannel = mock(ChatChannel.class);
    WebSocketSession session = mock(WebSocketSession.class);
    ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());

    when(chatChannel.chat("web", "hello"))
        .thenThrow(
            new RuntimeException(
                """
                HTTP 401 - {
                    "error": {
                        "message": "Incorrect API key provided: Test.",
                        "code": "invalid_api_key"
                    }
                }
                """));

    handler.handleTextMessage(
        session,
        new TextMessage(
            new ObjectMapper()
                .writeValueAsString(
                    Map.of(
                        "type", "userMessage",
                        "conversationId", "web",
                        "message", "hello"))));

    ArgumentCaptor<String[]> htmlCaptor = ArgumentCaptor.forClass(String[].class);
    var inOrder = inOrder(chatChannel);
    inOrder.verify(chatChannel).sendHtml(htmlCaptor.capture());
    inOrder.verify(chatChannel).chat("web", "hello");
    inOrder.verify(chatChannel).sendHtml(htmlCaptor.capture());
    verifyNoMoreInteractions(chatChannel);

    assertThat(String.join("", htmlCaptor.getAllValues().get(0)))
        .contains("hello")
        .contains("typing-indicator")
        .contains("ar-typing");

    assertThat(String.join("", htmlCaptor.getAllValues().get(1)))
        .contains("An error occurred while contacting the AI provider")
        .contains("Details: HTTP 401 - {")
        .contains("typing-indicator")
        .doesNotContain("ar-typing");
  }

  @Test
  void handleUserMessageShowsGenericProviderErrorForUnexpectedFailures() throws Exception {
    ChatChannel chatChannel = mock(ChatChannel.class);
    WebSocketSession session = mock(WebSocketSession.class);
    ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());

    when(chatChannel.chat(anyString(), anyString())).thenThrow(new RuntimeException("boom"));

    handler.handleTextMessage(
        session,
        new TextMessage(
            new ObjectMapper()
                .writeValueAsString(
                    Map.of(
                        "type", "userMessage",
                        "conversationId", "web",
                        "message", "hello"))));

    ArgumentCaptor<String[]> htmlCaptor = ArgumentCaptor.forClass(String[].class);
    var inOrder = inOrder(chatChannel);
    inOrder.verify(chatChannel).sendHtml(htmlCaptor.capture());
    inOrder.verify(chatChannel).chat("web", "hello");
    inOrder.verify(chatChannel).sendHtml(htmlCaptor.capture());

    assertThat(String.join("", htmlCaptor.getAllValues().get(1)))
        .contains("An error occurred while contacting the AI provider")
        .contains("Details: boom");
  }

  @Test
  void handleUserMessageLinksJobsCreatedDuringTheChatTurn() throws Exception {
    ChatChannel chatChannel = mock(ChatChannel.class);
    WebSocketSession session = mock(WebSocketSession.class);
    ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-summary.md");
    when(chatChannel.chat("web", "write a summary"))
        .thenReturn(new ChatTurnResult("I created a job.", List.of(reference)));

    handler.handleTextMessage(
        session,
        new TextMessage(
            new ObjectMapper()
                .writeValueAsString(
                    Map.of(
                        "type", "userMessage",
                        "conversationId", "web",
                        "message", "write a summary"))));

    ArgumentCaptor<String[]> htmlCaptor = ArgumentCaptor.forClass(String[].class);
    var ordered = inOrder(chatChannel);
    ordered.verify(chatChannel).sendHtml(htmlCaptor.capture());
    ordered.verify(chatChannel).chat("web", "write a summary");
    ordered.verify(chatChannel).sendHtml(htmlCaptor.capture());

    assertThat(String.join("", htmlCaptor.getAllValues().get(1)))
        .contains("I created a job.")
        .contains("View job")
        .contains("href=\"/jobs/2026-06-08/120000-write-summary.md\"");
  }

  @Test
  void handleChannelChangedSendsHistoryAndInputArea() throws Exception {
    ChatChannel chatChannel = mock(ChatChannel.class);
    WebSocketSession session = mock(WebSocketSession.class);
    ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());

    when(chatChannel.loadHistoryAsHtml("web")).thenReturn(List.of("<div>history</div>"));

    handler.handleTextMessage(
        session,
        new TextMessage(
            new ObjectMapper()
                .writeValueAsString(
                    Map.of(
                        "type", "channelChanged",
                        "conversationId", "web"))));

    ArgumentCaptor<String[]> htmlCaptor = ArgumentCaptor.forClass(String[].class);
    verify(chatChannel).sendHtml(htmlCaptor.capture());

    assertThat(String.join("", htmlCaptor.getValue()))
        .contains("chat-messages")
        .contains("history")
        .contains("chat-input-area")
        .contains("Message SEA...")
        .contains("<form id=\"chat-form\" ws-send")
        .doesNotContain("hx-trigger=\"keydown")
        .doesNotContain(
            "<textarea id=\"message-input\" class=\"textarea\" name=\"message\" rows=\"1\"\n"
                + "                                    placeholder=\"Message SEA...\"\n"
                + "                                    autocomplete=\"off\" spellcheck=\"true\" autofocus\n"
                + "                                    ws-send");
  }

  @Test
  void handleCreateConversationCreatesSelectsAndShowsWritableConversation() throws Exception {
    ChatChannel chatChannel = mock(ChatChannel.class);
    WebSocketSession session = mock(WebSocketSession.class);
    ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());

    when(chatChannel.createWebConversation()).thenReturn("web-new");
    when(chatChannel.conversationIds()).thenReturn(List.of("web", "web-new", "telegram-42"));
    when(chatChannel.loadHistoryAsHtml("web-new")).thenReturn(List.of("<div>new history</div>"));

    handler.handleTextMessage(
        session,
        new TextMessage(
            new ObjectMapper().writeValueAsString(Map.of("type", "createConversation"))));

    ArgumentCaptor<String[]> htmlCaptor = ArgumentCaptor.forClass(String[].class);
    verify(chatChannel).sendHtml(htmlCaptor.capture());

    assertThat(String.join("", htmlCaptor.getValue()))
        .contains("channel-selector")
        .contains("value=\"web-new\" selected")
        .contains("new-conversation-form")
        .contains("chat-messages")
        .contains("new history")
        .contains("chat-input-area")
        .contains("Message SEA...");
  }

  @Test
  void handleChannelChangedShowsWritableInputForAdditionalWebConversation() throws Exception {
    ChatChannel chatChannel = mock(ChatChannel.class);
    WebSocketSession session = mock(WebSocketSession.class);
    ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());

    when(chatChannel.loadHistoryAsHtml("web-new")).thenReturn(List.of("<div>history</div>"));

    handler.handleTextMessage(
        session,
        new TextMessage(
            new ObjectMapper()
                .writeValueAsString(
                    Map.of(
                        "type", "channelChanged",
                        "conversationId", "web-new"))));

    ArgumentCaptor<String[]> htmlCaptor = ArgumentCaptor.forClass(String[].class);
    verify(chatChannel).sendHtml(htmlCaptor.capture());

    assertThat(String.join("", htmlCaptor.getValue()))
        .contains("chat-input-area")
        .contains("Message SEA...")
        .doesNotContain("read-only view");
  }

  @Test
  void afterConnectionEstablishedSendsSelectorHistoryAndInputArea() throws Exception {
    ChatChannel chatChannel = mock(ChatChannel.class);
    WebSocketSession session = mock(WebSocketSession.class);
    ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());

    when(chatChannel.conversationIds()).thenReturn(List.of("web"));
    when(chatChannel.loadHistoryAsHtml("web")).thenReturn(List.of("<div>history</div>"));

    handler.afterConnectionEstablished(session);

    ArgumentCaptor<String[]> htmlCaptor = ArgumentCaptor.forClass(String[].class);
    verify(chatChannel).sendHtml(htmlCaptor.capture());

    assertThat(String.join("", htmlCaptor.getValue()))
        .contains("channel-selector")
        .contains("chat-messages")
        .contains("history")
        .contains("chat-input-area");
  }
}
