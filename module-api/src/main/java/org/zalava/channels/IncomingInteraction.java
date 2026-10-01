package org.zalava.channels;

import java.util.Objects;

/** One deduplicable interaction submitted by a channel module to the SEA-owned Core port. */
public record IncomingInteraction(
    String interactionId,
    ExternalIdentityReference identity,
    ChannelDestination destination,
    ChannelInteractionKind kind,
    ChannelInput input,
    String correlationId) {
  public IncomingInteraction {
    ChannelValues.requireNonBlank(interactionId, "interactionId");
    identity = Objects.requireNonNull(identity, "identity must not be null");
    destination = Objects.requireNonNull(destination, "destination must not be null");
    kind = Objects.requireNonNull(kind, "kind must not be null");
    input = Objects.requireNonNull(input, "input must not be null");
    ChannelValues.requireNonBlank(correlationId, "correlationId");
    if (!identity.channelId().equals(destination.channelId())) {
      throw new IllegalArgumentException("identity and destination channelId must match");
    }
  }
}
