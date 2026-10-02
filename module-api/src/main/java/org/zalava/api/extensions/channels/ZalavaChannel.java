package org.zalava.api.extensions.channels;

/**
 * A module-owned transport adapter. Core binds its ingress receiver and invokes delivery; the
 * module owns all protocol and SDK work behind this boundary.
 */
public interface ZalavaChannel extends AutoCloseable {
  ChannelDescriptor descriptor();

  void bind(ChannelInteractionReceiver receiver);

  /**
   * Starts or configures this transport with a host-owned, module-scoped context.
   *
   * <p>The default preserves compatibility with channels that require no configuration.
   */
  default void start(ChannelTransportContext context) {}

  void deliver(ChannelEvent event);

  @Override
  default void close() {}
}
