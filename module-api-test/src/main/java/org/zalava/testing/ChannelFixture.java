package org.zalava.testing;

import java.util.List;
import java.util.Objects;
import org.zalava.ZalavaModule;
import org.zalava.channels.ChannelEvent;
import org.zalava.channels.ChannelInteractionReceiver;
import org.zalava.channels.ZalavaChannel;

/** Contract-kit fixture for binding channel ingress and observing semantic delivery. */
public final class ChannelFixture {
  private final List<ZalavaChannel> channels;

  private ChannelFixture(List<ZalavaChannel> channels, ChannelInteractionReceiver receiver) {
    this.channels = channels;
    channels.forEach(channel -> channel.bind(receiver));
  }

  public static ChannelFixture bind(ZalavaModule module, ChannelInteractionReceiver receiver) {
    Objects.requireNonNull(module, "module must not be null");
    Objects.requireNonNull(receiver, "receiver must not be null");
    return new ChannelFixture(List.copyOf(module.channels()), receiver);
  }

  public List<ZalavaChannel> channels() {
    return channels;
  }

  public ZalavaChannel requireChannel(String channelId) {
    return channels.stream()
        .filter(channel -> channel.descriptor().channelId().equals(channelId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No channel with id " + channelId));
  }

  public void deliver(String channelId, ChannelEvent event) {
    requireChannel(channelId).deliver(Objects.requireNonNull(event, "event must not be null"));
  }
}
