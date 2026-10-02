package org.zalava.assistant.conversation.application.port.in;

import java.util.List;
import org.zalava.assistant.conversation.domain.ConversationMessage;

public interface ConversationQueries {
  List<String> findConversationIds();

  List<ConversationMessage> findByConversationId(String conversationId);
}
