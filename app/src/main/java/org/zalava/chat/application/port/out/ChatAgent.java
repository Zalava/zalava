package org.zalava.chat.application.port.out;

import org.zalava.chat.domain.ChatTurn;

public interface ChatAgent {
  ChatTurn respondTo(String conversationId, String message);
}
