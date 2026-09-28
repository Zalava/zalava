package org.zalava.chat.adapter.out.channels;

import org.zalava.channels.ChannelMessageReceivedEvent;
import org.zalava.channels.ChannelRegistry;
import org.zalava.chat.application.port.out.ChatMessageEvents;

public final class ChannelMessageEventAdapter implements ChatMessageEvents {
  private final ChannelRegistry registry;

  public ChannelMessageEventAdapter(ChannelRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void publishReceived(String channelName, String message) {
    registry.publishMessageReceivedEvent(new ChannelMessageReceivedEvent(channelName, message));
  }
}
