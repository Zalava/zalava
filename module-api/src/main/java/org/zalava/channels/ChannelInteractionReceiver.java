package org.zalava.channels;

/** SEA-owned Core ingress port implemented by the host, never by an agent. */
@FunctionalInterface
public interface ChannelInteractionReceiver {
  void receive(IncomingInteraction interaction);
}
