package org.zalava.assistant.chat.adapter.out.system;

import java.util.UUID;
import org.zalava.assistant.chat.application.port.out.ChatConversationIdGenerator;

public final class UuidChatConversationIdGenerator implements ChatConversationIdGenerator {
  @Override
  public String nextWebConversationId() {
    return "web-" + UUID.randomUUID();
  }
}
