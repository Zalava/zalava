package org.zalava.assistant.chat.application.port.out;

import org.zalava.assistant.chat.domain.ChatTurn;

public interface ChatAgent {
  ChatTurn respondTo(String conversationId, String message);
}
