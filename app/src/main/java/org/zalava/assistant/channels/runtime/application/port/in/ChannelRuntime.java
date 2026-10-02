package org.zalava.assistant.channels.runtime.application.port.in;

import java.util.List;
import org.zalava.api.extensions.channels.ChannelEvent;
import org.zalava.api.extensions.channels.IncomingInteraction;
import org.zalava.api.extensions.channels.ZalavaChannel;
import org.zalava.assistant.channels.runtime.domain.ChannelIngressResult;

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
