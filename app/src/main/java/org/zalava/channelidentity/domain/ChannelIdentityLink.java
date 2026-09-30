package org.zalava.channelidentity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.zalava.accounts.domain.Actor;

/** Persisted account-owned authority link for one stable external identity. */
public record ChannelIdentityLink(
    UUID id,
    ExternalChannelIdentity identity,
    Actor owner,
    ChannelOperationScope scope,
    Instant linkedAt,
    Instant revokedAt) {
  public ChannelIdentityLink {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(identity, "identity is required");
    Objects.requireNonNull(owner, "owner is required");
    Objects.requireNonNull(scope, "scope is required");
    Objects.requireNonNull(linkedAt, "linkedAt is required");
    if (revokedAt != null && revokedAt.isBefore(linkedAt)) {
      throw new IllegalArgumentException("revokedAt cannot precede linkedAt");
    }
  }

  public ChannelIdentityLink revoke(Instant revokedAt) {
    return new ChannelIdentityLink(
        id, identity, owner, scope, linkedAt, Objects.requireNonNull(revokedAt));
  }
}
