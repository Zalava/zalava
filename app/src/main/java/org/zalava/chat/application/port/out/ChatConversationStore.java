package org.zalava.chat.application.port.out;

import java.util.List;
import org.zalava.chat.domain.ChatMessage;

public interface ChatConversationStore {
  List<String> findConversationIds();

  List<ChatMessage> findByConversationId(String conversationId);

  void saveEmptyConversation(String conversationId);
}
