package org.zalava.channelidentity.domain;

import java.util.Objects;

/** A provider-stable subject reference. It is a lookup key, never an authority by itself. */
public record ExternalChannelIdentity(String channel, String subject) {
  public ExternalChannelIdentity {
    channel = required(channel, "channel", 64).toLowerCase(java.util.Locale.ROOT);
    subject = required(subject, "subject", 200);
    if (!channel.matches("[a-z][a-z0-9-]{0,63}")) {
      throw new IllegalArgumentException("Channel must be a stable kebab-case identifier");
    }
  }

  private static String required(String value, String field, int maximumLength) {
    Objects.requireNonNull(value, field + " is required");
    var normalized = value.trim();
    if (normalized.isEmpty() || normalized.length() > maximumLength) {
      throw new IllegalArgumentException(
          field + " must contain between 1 and " + maximumLength + " characters");
    }
    return normalized;
  }
}
