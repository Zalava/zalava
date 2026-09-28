package org.zalava.channels.application.port.in;

import java.util.Optional;

public interface ChannelApprovalCommands {
  Optional<String> handle(String message);
}
