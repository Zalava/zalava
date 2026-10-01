package org.zalava.channelruntime.application.port.in;

import java.util.List;
import org.zalava.channelruntime.domain.ChannelIngressResult;
import org.zalava.channels.ChannelEvent;
import org.zalava.channels.IncomingInteraction;
import org.zalava.channels.ZalavaChannel;

/** SEA-owned registry and routing boundary for module-provided channels. */
public interface ChannelRuntime extends AutoCloseable {
  void register(ZalavaChannel channel);

  default void registerAll(List<ZalavaChannel> channels) {
    channels.forEach(this::register);
  }

  ChannelIngressResult receive(IncomingInteraction interaction);

  boolean deliver(ChannelEvent event);

  @Override
  void close();
}
