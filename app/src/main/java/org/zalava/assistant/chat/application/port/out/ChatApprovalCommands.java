package org.zalava.assistant.chat.application.port.out;

import java.util.Optional;

public interface ChatApprovalCommands {
  Optional<String> handle(String message);
}
