package org.zalava.api.extensions.channels;

/**
 * A transport-verified stable subject reference. It is not a username, phone number, or Zalava
 * authority; Core resolves it before accepting an interaction.
 */
public record ExternalIdentityReference(String channelId, String subject) {
  public ExternalIdentityReference {
    ChannelValues.requireNonBlank(channelId, "channelId");
    ChannelValues.requireNonBlank(subject, "subject");
  }
}
