package org.zalava.assistant.chat.application.port.in;

import org.zalava.assistant.chat.domain.ChatTurn;

public interface ChatCommands {
  ChatTurn chat(String conversationId, String message);

  String createWebConversation();
}
