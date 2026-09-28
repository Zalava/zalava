package org.zalava.tasks.adapter.out.channel;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.zalava.channels.Channel;
import org.zalava.channels.ChannelRegistry;
import org.zalava.tasks.domain.Task;
import org.junit.jupiter.api.Test;

class ChannelTaskNotifierTest {

  @Test
  void sendsCompletedTaskMessageToLatestChannel() {
    ChannelRegistry channelRegistry = mock(ChannelRegistry.class);
    Channel channel = mock(Channel.class);
    when(channelRegistry.getLatestChannel()).thenReturn(channel);
    ChannelTaskNotifier notifier = new ChannelTaskNotifier(channelRegistry);

    notifier.notify("Release", Task.Status.completed, "Published");

    verify(channel).sendMessage("📋 Task 'Release' completed:\nPublished");
  }

  @Test
  void sendsWaitingTaskMessageToLatestChannel() {
    ChannelRegistry channelRegistry = mock(ChannelRegistry.class);
    Channel channel = mock(Channel.class);
    when(channelRegistry.getLatestChannel()).thenReturn(channel);
    ChannelTaskNotifier notifier = new ChannelTaskNotifier(channelRegistry);

    notifier.notify("Release", Task.Status.awaiting_human_input, "Approve publish");

    verify(channel).sendMessage("📋 Task 'Release' is waiting for your input:\nApprove publish");
  }

  @Test
  void sendsSeaApprovalPromptToLatestChannel() {
    ChannelRegistry channelRegistry = mock(ChannelRegistry.class);
    Channel channel = mock(Channel.class);
    when(channelRegistry.getLatestChannel()).thenReturn(channel);
    ChannelTaskNotifier notifier = new ChannelTaskNotifier(channelRegistry);

    notifier.notify(
        "Release",
        Task.Status.awaiting_human_input,
        """
                SEA is waiting for your approval before continuing this job.

                Request approval-123
                SEA requests approval to run publish on provider release-provider for actor telegram-42.
                Allow once: /sea approve approval-123
                Always allow tool: /sea always-allow-tool approval-123
                Deny: /sea deny approval-123
                """);

    verify(channel)
        .sendMessage(
            """
                📋 Task 'Release' is waiting for your input:
                SEA is waiting for your approval before continuing this job.

                Request approval-123
                SEA requests approval to run publish on provider release-provider for actor telegram-42.
                Allow once: /sea approve approval-123
                Always allow tool: /sea always-allow-tool approval-123
                Deny: /sea deny approval-123
                """);
  }

  @Test
  void ignoresStatusesThatDoNotNeedNotification() {
    ChannelRegistry channelRegistry = mock(ChannelRegistry.class);
    Channel channel = mock(Channel.class);
    when(channelRegistry.getLatestChannel()).thenReturn(channel);
    ChannelTaskNotifier notifier = new ChannelTaskNotifier(channelRegistry);

    notifier.notify("Release", Task.Status.todo, "Queued");

    verifyNoInteractions(channel);
  }

  @Test
  void doesNotFailTaskWhenChannelNotificationFails() {
    ChannelRegistry channelRegistry = mock(ChannelRegistry.class);
    Channel channel = mock(Channel.class);
    when(channelRegistry.getLatestChannel()).thenReturn(channel);
    doThrow(new IllegalStateException("Disconnected"))
        .when(channel)
        .sendMessage("📋 Task 'Release' completed:\nPublished");
    ChannelTaskNotifier notifier = new ChannelTaskNotifier(channelRegistry);

    assertThatCode(() -> notifier.notify("Release", Task.Status.completed, "Published"))
        .doesNotThrowAnyException();
  }
}
