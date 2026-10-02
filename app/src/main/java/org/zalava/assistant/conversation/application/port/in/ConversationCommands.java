package org.zalava.assistant.conversation.application.port.in;

import java.util.List;
import org.zalava.assistant.conversation.domain.ConversationMessage;

public interface ConversationCommands {
  void appendAll(String conversationId, List<ConversationMessage> messages);

  void saveAll(String conversationId, List<ConversationMessage> messages);

  void deleteByConversationId(String conversationId);
}
