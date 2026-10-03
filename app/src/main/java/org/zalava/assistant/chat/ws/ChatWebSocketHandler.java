package org.zalava.assistant.chat.ws;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.zalava.assistant.chat.ChatChannel;
import org.zalava.assistant.chat.ChatHtml;
import org.zalava.assistant.chat.ChatTurnResult;
import org.zalava.assistant.chat.Htmx;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.chat.application.port.in.ActorChatStreamListener;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.web.ui.protocol.UiCommand;
import org.zalava.web.ui.protocol.UiCommandDecoder;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
    name = "sea.chat.transport",
    havingValue = "spring-websocket",
    matchIfMissing = true)
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

  private final ChatChannel chatChannel;
  private final ObjectMapper objectMapper;
  private final AuthenticatedActorResolver actors;
  private final ActorWebSocketSessions sessions;
  private final ActorChatCommands actorCommands;
  private final ActorChatQueries actorQueries;
  private final org.zalava.assistant.conversation.application.port.in.ConversationContinuation
      continuation;

  public ChatWebSocketHandler(ChatChannel chatChannel, ObjectMapper objectMapper) {
    this(chatChannel, objectMapper, null, null, null, null);
  }

  public ChatWebSocketHandler(
      ChatChannel chatChannel,
      ObjectMapper objectMapper,
      AuthenticatedActorResolver actors,
      ActorWebSocketSessions sessions,
      ActorChatCommands actorCommands,
      ActorChatQueries actorQueries) {
    this(chatChannel, objectMapper, actors, sessions, actorCommands, actorQueries, null);
  }

  @Autowired
  public ChatWebSocketHandler(
      ChatChannel chatChannel,
      ObjectMapper objectMapper,
      AuthenticatedActorResolver actors,
      ActorWebSocketSessions sessions,
      ActorChatCommands actorCommands,
      ActorChatQueries actorQueries,
      org.zalava.assistant.conversation.application.port.in.ConversationContinuation continuation) {
    this.continuation = continuation;
    this.chatChannel = chatChannel;
    this.objectMapper = objectMapper;
    this.actors = actors;
    this.sessions = sessions;
    this.actorCommands = actorCommands;
    this.actorQueries = actorQueries;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Actor actor = actor(session);
    if (actor == null) {
      chatChannel.setWsSession(session);
      sendLegacyInitialState(session);
      return;
    }

    sessions.connect(actor, session);
    log.info("WebChat WebSocket connected: {}", session.getId());
    List<ConversationReference> references = actorQueries.conversations(actor);
    ConversationReference selected =
        references.isEmpty() ? actorCommands.createWebConversation(actor) : references.getFirst();
    if (references.isEmpty()) references = List.of(selected);
    sendToSession(
        session,
        Htmx.oobInnerHtml(
            "channel-selector",
            ChatHtml.conversationSelector(values(references), selected.value())),
        Htmx.oobInnerHtml("chat-messages", historyHtml(actor, selected)),
        Htmx.oobInnerHtml("chat-input-area", ChatHtml.chatInputArea(selected.value())));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    if (actors == null) chatChannel.clearWsSession(session);
    else sessions.disconnect(session);
    log.info("WebChat WebSocket disconnected: {} ({})", session.getId(), status);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
    UiCommandDecoder.decode(payload).ifPresent(command -> dispatch(session, command));
  }

  private void dispatch(WebSocketSession session, UiCommand command) {
    try {
      switch (command) {
        case UiCommand.SelectConversation select ->
            handleChannelChanged(session, Map.of("conversationId", select.conversationId()));
        case UiCommand.CreateConversation ignored -> handleCreateConversation(session);
        case UiCommand.SendChat send ->
            handleUserMessage(
                session, Map.of("conversationId", send.conversationId(), "message", send.text()));
        case UiCommand.ContinueConversation ignored -> {
          // Explicit continuation is available through the product JSON adapter.
        }
        case UiCommand.DecideApproval ignored -> {
          // Approval decisions are intentionally available only on the authenticated SEA UI
          // adapter.
        }
        case UiCommand.PutAttachment ignored -> {
          // Attachments are intentionally available only on the authenticated SEA UI adapter.
        }
        case UiCommand.DeleteAttachment ignored -> {
          // Attachments are intentionally available only on the authenticated SEA UI adapter.
        }
      }
    } catch (Exception exception) {
      if (exception instanceof RuntimeException runtimeException) throw runtimeException;
      throw new IllegalStateException("Unable to dispatch UI command", exception);
    }
  }

  private void handleChannelChanged(WebSocketSession session, Map<String, Object> payload)
      throws Exception {
    String conversationId = (String) payload.get("conversationId");
    if (conversationId == null || conversationId.isBlank()) return;

    Actor actor = actor(session);
    if (actor != null) {
      ConversationReference reference = reference(conversationId);
      sendToSession(
          session,
          Htmx.oobInnerHtml("chat-messages", historyHtml(actor, reference)),
          Htmx.oobInnerHtml("chat-input-area", ChatHtml.chatInputArea(reference.value())));
      return;
    }

    String bubbles =
        String.join(System.lineSeparator(), chatChannel.loadHistoryAsHtml(conversationId));
    sendHtml(
        session,
        Htmx.oobInnerHtml("chat-messages", bubbles),
        Htmx.oobInnerHtml("chat-input-area", ChatHtml.chatInputArea(conversationId)));
  }

  private void handleCreateConversation(WebSocketSession session) throws Exception {
    Actor actor = actor(session);
    if (actor != null) {
      ConversationReference selected = actorCommands.createWebConversation(actor);
      sendToSession(
          session,
          Htmx.oobInnerHtml(
              "channel-selector",
              ChatHtml.conversationSelector(
                  values(actorQueries.conversations(actor)), selected.value())),
          Htmx.oobInnerHtml("chat-messages", historyHtml(actor, selected)),
          Htmx.oobInnerHtml("chat-input-area", ChatHtml.chatInputArea(selected.value())));
      return;
    }

    String conversationId = chatChannel.createWebConversation();
    sendHtml(
        session,
        Htmx.oobInnerHtml(
            "channel-selector",
            ChatHtml.conversationSelector(chatChannel.conversationIds(), conversationId)),
        Htmx.oobInnerHtml(
            "chat-messages",
            String.join(System.lineSeparator(), chatChannel.loadHistoryAsHtml(conversationId))),
        Htmx.oobInnerHtml("chat-input-area", ChatHtml.chatInputArea(conversationId)));
  }

  private void handleUserMessage(WebSocketSession session, Map<String, Object> payload)
      throws Exception {
    String conversationId = (String) payload.get("conversationId");
    String userMessage = (String) payload.get("message");
    if (userMessage == null || userMessage.isBlank()) return;
    userMessage = userMessage.trim();
    Actor actor = actor(session);
    if (actor != null && (conversationId == null || conversationId.isBlank())) return;
    if (actor == null && (conversationId == null || conversationId.isBlank()))
      conversationId = "web";

    sendHtml(
        session,
        Htmx.oobAppend("chat-messages", ChatHtml.userBubble(userMessage)),
        Htmx.oobReplace("typing-indicator", ChatHtml.typingDots()));

    try {
      if (actor != null) {
        streamActorChat(session, actor, conversationId, userMessage);
        return;
      }
      ChatTurnResult response = chatChannel.chat(conversationId, userMessage);
      sendHtml(
          session,
          Htmx.oobAppend(
              "chat-messages", ChatHtml.agentBubble(response.text(), response.jobReferences())),
          Htmx.oobReplace("typing-indicator", ""));
    } catch (RuntimeException exception) {
      log.warn("Chat request failed for conversation {}", conversationId, exception);
      sendHtml(
          session,
          Htmx.oobAppend("chat-messages", ChatHtml.agentBubble(genericUserFacingError(exception))),
          Htmx.oobReplace("typing-indicator", ""));
    }
  }

  /**
   * Streams the actor turn to the session: the first delta appends a streaming agent bubble,
   * subsequent deltas replace its content, and completion swaps it for the final bubble including
   * any "View job" links. The typing indicator stays visible until the turn completes.
   */
  private void streamActorChat(
      WebSocketSession session, Actor actor, String conversationId, String userMessage)
      throws Exception {
    StringBuilder streamed = new StringBuilder();
    if (continuation != null)
      continuation.requireWeb(actor, new ConversationReference(conversationId));
    actorCommands.streamChat(
        actor,
        reference(conversationId),
        userMessage,
        new ActorChatStreamListener() {
          @Override
          public void onDelta(String text) {
            streamed.append(text);
            try {
              if (streamed.length() == text.length()) {
                sendToSession(
                    session,
                    Htmx.oobAppend(
                        "chat-messages", ChatHtml.streamingAgentBubble(streamed.toString())));
              } else {
                sendToSession(session, Htmx.oobInnerHtml("streaming-bubble", streamed.toString()));
              }
            } catch (IOException exception) {
              throw new IllegalStateException("Unable to stream the chat response", exception);
            }
          }

          @Override
          public void onComplete(String fullText, List<ActorTaskReference> jobReferences) {
            try {
              sendToSession(
                  session,
                  Htmx.oobDelete("streaming-bubble"),
                  Htmx.oobAppend(
                      "chat-messages", ChatHtml.actorAgentBubble(fullText, jobReferences)),
                  Htmx.oobReplace("typing-indicator", ""));
            } catch (IOException exception) {
              throw new IllegalStateException("Unable to finalize the chat response", exception);
            }
          }

          @Override
          public void onError(RuntimeException failure) {
            // Rendered by the outer catch, which also clears the typing indicator.
          }
        });
  }

  private Actor actor(WebSocketSession session) {
    if (actors == null) return null;
    if (session.getPrincipal() == null)
      throw new AccessDeniedException("An authenticated account is required");
    return actors.actorForLogin(session.getPrincipal().getName());
  }

  private void sendLegacyInitialState(WebSocketSession session) throws IOException {
    log.info("WebChat WebSocket connected: {}", session.getId());
    List<String> ids = chatChannel.conversationIds();
    String selected = ids.getFirst();
    sendHtml(
        session,
        Htmx.oobInnerHtml("channel-selector", ChatHtml.conversationSelector(ids, selected)),
        Htmx.oobInnerHtml(
            "chat-messages",
            String.join(System.lineSeparator(), chatChannel.loadHistoryAsHtml(selected))),
        Htmx.oobInnerHtml("chat-input-area", ChatHtml.chatInputArea(selected)));
  }

  private String historyHtml(Actor actor, ConversationReference reference) {
    List<ChatMessage> history = actorQueries.history(actor, reference);
    if (history.isEmpty())
      return ChatHtml.agentBubble("Hi! I'm your SEA assistant. How can I help you today?");
    return history.stream()
        .filter(message -> message.role() != ChatMessage.Role.SYSTEM)
        .map(
            message ->
                message.role() == ChatMessage.Role.USER
                    ? ChatHtml.userBubble(message.text())
                    : ChatHtml.agentBubble(message.text()))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private void sendHtml(WebSocketSession session, String... html) throws IOException {
    if (actor(session) == null) chatChannel.sendHtml(html);
    else sendToSession(session, html);
  }

  private static ConversationReference reference(String value) {
    return new ConversationReference(value);
  }

  private static List<String> values(List<ConversationReference> references) {
    return references.stream().map(ConversationReference::value).toList();
  }

  private static void sendToSession(WebSocketSession session, String... html) throws IOException {
    if (session.isOpen())
      session.sendMessage(new TextMessage(String.join(System.lineSeparator(), html)));
  }

  private static String genericUserFacingError(RuntimeException exception) {
    return "An error occurred while contacting the AI provider.\nDetails: "
        + summarizeError(exception);
  }

  private static String summarizeError(Throwable exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }
}
