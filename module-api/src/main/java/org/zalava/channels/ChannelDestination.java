package org.zalava.channels;

import java.util.Objects;

/**
 * Opaque channel-owned delivery handle. Its value is never interpreted by Core and must not be
 * treated as a chat identifier or identity authority.
 */
public record ChannelDestination(String channelId, String deliveryHandle, ChannelPrivacy privacy) {
  public ChannelDestination {
    ChannelValues.requireNonBlank(channelId, "channelId");
    ChannelValues.requireNonBlank(deliveryHandle, "deliveryHandle");
    privacy = Objects.requireNonNull(privacy, "privacy must not be null");
  }
}
