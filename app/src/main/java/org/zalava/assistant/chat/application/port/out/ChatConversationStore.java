package org.zalava.assistant.chat.application.port.out;

import java.util.List;
import org.zalava.assistant.chat.domain.ChatMessage;

public interface ChatConversationStore {
  List<String> findConversationIds();

  List<ChatMessage> findByConversationId(String conversationId);

  void saveEmptyConversation(String conversationId);
}
