package org.zalava.channels;

import java.util.Objects;

/** Stable declaration of one module-provided channel transport. */
public record ChannelDescriptor(
    String channelId, String displayName, ChannelCapabilities capabilities) {
  public ChannelDescriptor {
    if (channelId == null || channelId.isBlank()) {
      throw new IllegalArgumentException("channelId must not be blank");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
    capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");
  }
}
