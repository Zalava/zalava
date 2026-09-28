package org.zalava.chat.adapter.out.system;

import java.util.UUID;
import org.zalava.chat.application.port.out.ChatConversationIdGenerator;

public final class UuidChatConversationIdGenerator implements ChatConversationIdGenerator {
  @Override
  public String nextWebConversationId() {
    return "web-" + UUID.randomUUID();
  }
}
