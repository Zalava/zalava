package org.zalava.chat.adapter.out.conversation;

import java.util.List;
import org.zalava.chat.application.port.out.ChatConversationStore;
import org.zalava.chat.domain.ChatMessage;
import org.zalava.conversation.application.port.in.ConversationRepository;

public final class ConversationChatStore implements ChatConversationStore {
  private final ConversationRepository conversations;

  public ConversationChatStore(ConversationRepository conversations) {
    this.conversations = conversations;
  }

  @Override
  public List<String> findConversationIds() {
    return conversations.findConversationIds();
  }

  @Override
  public List<ChatMessage> findByConversationId(String id) {
    return conversations.findByConversationId(id).stream()
        .map(
            message ->
                new ChatMessage(ChatMessage.Role.valueOf(message.role().name()), message.text()))
        .toList();
  }

  @Override
  public void saveEmptyConversation(String id) {
    conversations.saveAll(id, List.of());
  }
}
