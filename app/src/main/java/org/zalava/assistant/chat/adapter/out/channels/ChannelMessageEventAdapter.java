package org.zalava.assistant.chat.adapter.out.channels;

import org.zalava.assistant.channels.ChannelMessageReceivedEvent;
import org.zalava.assistant.channels.ChannelRegistry;
import org.zalava.assistant.chat.application.port.out.ChatMessageEvents;

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
