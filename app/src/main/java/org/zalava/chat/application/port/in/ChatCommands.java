package org.zalava.chat.application.port.in;

import org.zalava.chat.domain.ChatTurn;

public interface ChatCommands {
  ChatTurn chat(String conversationId, String message);

  String createWebConversation();
}
