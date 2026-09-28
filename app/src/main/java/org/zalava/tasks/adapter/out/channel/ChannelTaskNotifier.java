package org.zalava.tasks.adapter.out.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zalava.channels.Channel;
import org.zalava.channels.ChannelRegistry;
import org.zalava.tasks.application.port.out.TaskNotifier;
import org.zalava.tasks.domain.Task;

@Component
public class ChannelTaskNotifier implements TaskNotifier {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChannelTaskNotifier.class);

  private final ChannelRegistry channelRegistry;

  public ChannelTaskNotifier(ChannelRegistry channelRegistry) {
    this.channelRegistry = channelRegistry;
  }

  @Override
  public void notify(String taskName, Task.Status status, String feedback) {
    try {
      Channel channel = channelRegistry.getLatestChannel();
      if (channel == null) {
        return;
      }
      if (status == Task.Status.completed) {
        channel.sendMessage("📋 Task '%s' completed:\n%s".formatted(taskName, feedback));
      } else if (status == Task.Status.awaiting_human_input) {
        channel.sendMessage(
            "📋 Task '%s' is waiting for your input:\n%s".formatted(taskName, feedback));
      }
    } catch (Exception exception) {
      LOGGER.warn("Failed to notify user about task '{}': {}", taskName, exception.getMessage());
    }
  }
}
