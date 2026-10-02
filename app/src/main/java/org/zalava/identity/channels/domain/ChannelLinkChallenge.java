package org.zalava.identity.channels.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.zalava.identity.accounts.domain.Actor;

/** One-time, account-owned proof that a transport observed the presented code. */
public record ChannelLinkChallenge(
    UUID id,
    Actor owner,
    String channel,
    ChannelOperationScope scope,
    String codeHash,
    Instant expiresAt,
    Instant consumedAt) {
  public ChannelLinkChallenge {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(owner, "owner is required");
    channel = new ExternalChannelIdentity(channel, "subject").channel();
    Objects.requireNonNull(scope, "scope is required");
    Objects.requireNonNull(codeHash, "codeHash is required");
    Objects.requireNonNull(expiresAt, "expiresAt is required");
  }

  public ChannelLinkChallenge consume(Instant consumedAt) {
    return new ChannelLinkChallenge(
        id, owner, channel, scope, codeHash, expiresAt, Objects.requireNonNull(consumedAt));
  }
}
