package org.zalava.chat.application;

import java.util.ArrayList;
import java.util.List;
import org.zalava.chat.application.port.in.ChatCommands;
import org.zalava.chat.application.port.in.ChatQueries;
import org.zalava.chat.application.port.out.ChatAgent;
import org.zalava.chat.application.port.out.ChatApprovalCommands;
import org.zalava.chat.application.port.out.ChatConversationIdGenerator;
import org.zalava.chat.application.port.out.ChatConversationStore;
import org.zalava.chat.application.port.out.ChatMessageEvents;
import org.zalava.chat.domain.ChatMessage;
import org.zalava.chat.domain.ChatTurn;

public final class DefaultChatUseCases implements ChatCommands, ChatQueries {
  public static final String WEB_CONVERSATION_ID = "web";
  public static final String WEB_CHANNEL_NAME = "Web Chat Channel";

  private final ChatAgent agent;
  private final ChatApprovalCommands approvalCommands;
  private final ChatConversationStore conversations;
  private final ChatMessageEvents messageEvents;
  private final ChatConversationIdGenerator idGenerator;

  public DefaultChatUseCases(
      ChatAgent agent,
      ChatApprovalCommands approvalCommands,
      ChatConversationStore conversations,
      ChatMessageEvents messageEvents,
      ChatConversationIdGenerator idGenerator) {
    this.agent = agent;
    this.approvalCommands = approvalCommands;
    this.conversations = conversations;
    this.messageEvents = messageEvents;
    this.idGenerator = idGenerator;
  }

  @Override
  public ChatTurn chat(String conversationId, String message) {
    messageEvents.publishReceived(WEB_CHANNEL_NAME, message);
    return approvalCommands
        .handle(message)
        .map(response -> new ChatTurn(response, List.of()))
        .orElseGet(() -> agent.respondTo(conversationId, message));
  }

  @Override
  public String createWebConversation() {
    String conversationId = idGenerator.nextWebConversationId();
    conversations.saveEmptyConversation(conversationId);
    return conversationId;
  }

  @Override
  public List<String> conversationIds() {
    List<String> result = new ArrayList<>();
    result.add(WEB_CONVERSATION_ID);
    conversations.findConversationIds().stream()
        .filter(id -> !WEB_CONVERSATION_ID.equals(id))
        .forEach(result::add);
    return result;
  }

  @Override
  public List<ChatMessage> history(String conversationId) {
    return conversations.findByConversationId(conversationId);
  }
}
