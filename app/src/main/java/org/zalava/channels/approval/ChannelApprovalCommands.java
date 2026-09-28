package org.zalava.channels.approval;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.channels.adapter.out.approval.ProviderOperationChannelAdapter;
import org.zalava.channels.adapter.out.approval.SeaChannelApprovalStore;
import org.zalava.channels.adapter.out.tasks.TaskChannelAdapter;
import org.zalava.channels.application.DefaultChannelApprovalCommands;
import org.zalava.operation.application.port.in.ProviderToolOperations;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;

/** Compatibility adapter retained for existing channel and chat callers. */
@Component
public class ChannelApprovalCommands {
  private final org.zalava.channels.application.port.in.ChannelApprovalCommands commands;

  @Deprecated
  public ChannelApprovalCommands(
      SeaToolApprovalRequests approvals,
      ProviderToolOperations operations,
      TaskCommands taskCommands,
      TaskQueries taskQueries) {
    this(
        new DefaultChannelApprovalCommands(
            new SeaChannelApprovalStore(approvals),
            new ProviderOperationChannelAdapter(operations),
            new TaskChannelAdapter(taskCommands, taskQueries)));
  }

  @Autowired
  public ChannelApprovalCommands(
      org.zalava.channels.application.port.in.ChannelApprovalCommands commands) {
    this.commands = commands;
  }

  public Optional<String> handle(String message) {
    return commands.handle(message);
  }
}
