package org.zalava.assistant.chat.adapter.out.channels;

import java.util.Optional;
import org.zalava.assistant.channels.approval.ChannelApprovalCommands;
import org.zalava.assistant.chat.application.port.out.ChatApprovalCommands;

public final class ChannelApprovalCommandAdapter implements ChatApprovalCommands {
  private final ChannelApprovalCommands commands;

  public ChannelApprovalCommandAdapter(ChannelApprovalCommands commands) {
    this.commands = commands;
  }

  @Override
  public Optional<String> handle(String message) {
    return commands.handle(message);
  }
}
