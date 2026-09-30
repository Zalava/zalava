package org.zalava.channels;

/**
 * A module-owned transport adapter. Core binds its ingress receiver and invokes delivery; the
 * module owns all protocol and SDK work behind this boundary.
 */
public interface ZalavaChannel extends AutoCloseable {
  ChannelDescriptor descriptor();

  void bind(ChannelInteractionReceiver receiver);

  void deliver(ChannelEvent event);

  @Override
  default void close() {}
}
