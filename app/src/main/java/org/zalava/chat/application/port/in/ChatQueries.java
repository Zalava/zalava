package org.zalava.chat.application.port.in;

import java.util.List;
import org.zalava.chat.domain.ChatMessage;

public interface ChatQueries {
  List<String> conversationIds();

  List<ChatMessage> history(String conversationId);
}
