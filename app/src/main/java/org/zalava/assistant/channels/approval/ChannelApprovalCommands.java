package org.zalava.assistant.channels.approval;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zalava.assistant.channels.adapter.out.approval.ProviderOperationChannelAdapter;
import org.zalava.assistant.channels.adapter.out.approval.ZalavaChannelApprovalStore;
import org.zalava.assistant.channels.adapter.out.tasks.TaskChannelAdapter;
import org.zalava.assistant.channels.application.DefaultChannelApprovalCommands;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;

/** Compatibility adapter retained for existing channel and chat callers. */
@Component
public class ChannelApprovalCommands {
  private final org.zalava.assistant.channels.application.port.in.ChannelApprovalCommands commands;

  @Deprecated
  public ChannelApprovalCommands(
      ZalavaToolApprovalRequests approvals,
      ProviderToolOperations operations,
      TaskCommands taskCommands,
      TaskQueries taskQueries) {
    this(
        new DefaultChannelApprovalCommands(
            new ZalavaChannelApprovalStore(approvals),
            new ProviderOperationChannelAdapter(operations),
            new TaskChannelAdapter(taskCommands, taskQueries)));
  }

  @Autowired
  public ChannelApprovalCommands(
      org.zalava.assistant.channels.application.port.in.ChannelApprovalCommands commands) {
    this.commands = commands;
  }

  public Optional<String> handle(String message) {
    return commands.handle(message);
  }
}
