package org.zalava.assistant.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.agent.Agent;
import org.zalava.assistant.channels.Channel;
import org.zalava.assistant.channels.ChannelRegistry;
import org.zalava.assistant.channels.approval.ChannelApprovalCommands;
import org.zalava.assistant.chat.adapter.out.channels.ChannelApprovalCommandAdapter;
import org.zalava.assistant.chat.adapter.out.channels.ChannelMessageEventAdapter;
import org.zalava.assistant.chat.adapter.out.springai.SpringAiChatConversationStore;
import org.zalava.assistant.chat.adapter.out.system.UuidChatConversationIdGenerator;
import org.zalava.assistant.chat.application.DefaultChatUseCases;
import org.zalava.assistant.chat.application.port.in.ChatCommands;
import org.zalava.assistant.chat.application.port.in.ChatQueries;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.chat.domain.ChatTurn;

/**
 * GUI channel for the web chat interface. Pushes messages directly to the active WebSocket session
 * when connected, falling back to an in-memory queue for REST polling.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
    name = "sea.chat.transport",
    havingValue = "spring-websocket",
    matchIfMissing = true)
public class ChatChannel implements Channel {

  private static final Logger log = LoggerFactory.getLogger(ChatChannel.class);

  private final ChatCommands commands;
  private final ChatQueries queries;
  private final ConcurrentLinkedQueue<String> pendingMessages = new ConcurrentLinkedQueue<>();
  private final AtomicReference<WebSocketSession> wsSession = new AtomicReference<>();

  @Autowired
  public ChatChannel(
      ChatCommands commands,
      @Qualifier("chatCommands") ChatQueries queries,
      ChannelRegistry channelRegistry) {
    this.commands = commands;
    this.queries = queries;
    channelRegistry.registerChannel(this);
    log.info("Started Web Chat channel");
  }

  /**
   * Compatibility constructor for current tests and transitional channel callers. New composition
   * uses the application ports constructor above.
   */
  @Deprecated
  public ChatChannel(
      Agent agent,
      ChannelRegistry channelRegistry,
      ChatMemoryRepository repository,
      org.zalava.tasks.capture.TaskCreationContext taskCreationContext,
      ChannelApprovalCommands approvalCommands) {
    this(
        legacyCommands(agent, channelRegistry, repository, taskCreationContext, approvalCommands),
        channelRegistry);
  }

  private ChatChannel(ChatCommands commands, ChannelRegistry channelRegistry) {
    this(commands, (ChatQueries) commands, channelRegistry);
  }

  private static ChatCommands legacyCommands(
      Agent agent,
      ChannelRegistry registry,
      ChatMemoryRepository repository,
      org.zalava.tasks.capture.TaskCreationContext taskCreationContext,
      ChannelApprovalCommands approvalCommands) {
    return new DefaultChatUseCases(
        (conversationId, message) -> {
          var capture = taskCreationContext.capture(() -> agent.respondTo(conversationId, message));
          return new ChatTurn(capture.value(), capture.taskReferences());
        },
        new ChannelApprovalCommandAdapter(approvalCommands),
        new SpringAiChatConversationStore(repository),
        new ChannelMessageEventAdapter(registry),
        new UuidChatConversationIdGenerator());
  }

  @Override
  public String getName() {
    return "Web Chat Channel";
  }

  /** Called by the WebSocket handler when a client connects. */
  public void setWsSession(WebSocketSession session) {
    wsSession.set(session);
  }

  /** Called by the WebSocket handler when the client disconnects. */
  public void clearWsSession(WebSocketSession session) {
    wsSession.compareAndSet(session, null);
  }

  /**
   * Sends a raw HTML fragment to the active WebSocket session. Used by the WebSocket handler to
   * push user/agent bubbles and typing indicators.
   */
  public void sendHtml(String... html) throws IOException {
    WebSocketSession session = wsSession.get();
    if (session != null && session.isOpen()) {
      session.sendMessage(new TextMessage(String.join(System.lineSeparator(), html)));
    }
  }

  /**
   * Delivers a background-task message. Pushes directly to WebSocket if a session is open,
   * otherwise buffers for REST polling.
   */
  @Override
  public void sendMessage(String message) {
    try {
      sendHtml(buildBackgroundMessageHtml(message));
    } catch (IOException e) {
      log.warn("WS push failed, buffering message: {}", e.getMessage());
      pendingMessages.add(message);
    }
  }

  /** Returns all known conversation IDs, always with "web" first. */
  public List<String> conversationIds() {
    List<String> result = new ArrayList<>();
    result.add("web");
    queries.conversationIds().stream().filter(id -> !id.equals("web")).forEach(result::add);
    return result;
  }

  public String createWebConversation() {
    return commands.createWebConversation();
  }

  /**
   * Loads conversation history for the given conversationId as HTML bubbles. Returns a single
   * welcome bubble if no history exists yet.
   */
  public List<String> loadHistoryAsHtml(String conversationId) {
    List<ChatMessage> history = queries.history(conversationId);
    if (history.isEmpty()) {
      return List.of(ChatHtml.agentBubble("Hi! I'm your SEA assistant. How can I help you today?"));
    }
    List<String> bubbles = new ArrayList<>();
    for (ChatMessage msg : history) {
      if (msg.role() == ChatMessage.Role.USER) bubbles.add(ChatHtml.userBubble(msg.text()));
      else if (msg.role() == ChatMessage.Role.ASSISTANT)
        bubbles.add(ChatHtml.agentBubble(msg.text()));
    }
    return bubbles;
  }

  /** Handles a chat message from the web UI for the given conversationId. */
  public ChatTurnResult chat(String conversationId, String message) {
    ChatTurn response = commands.chat(conversationId, message);
    return new ChatTurnResult(response.text(), response.taskReferences());
  }

  private static String buildBackgroundMessageHtml(String text) {
    return Htmx.oobAppend("chat-messages", ChatHtml.agentBubble(text));
  }
}
