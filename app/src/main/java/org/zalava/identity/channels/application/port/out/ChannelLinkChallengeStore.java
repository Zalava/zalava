package org.zalava.identity.channels.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.zalava.identity.channels.domain.ChannelLinkChallenge;

public interface ChannelLinkChallengeStore {
  Optional<ChannelLinkChallenge> findActiveByCodeHash(String codeHash, Instant now);

  ChannelLinkChallenge save(ChannelLinkChallenge challenge);

  boolean consume(UUID id, Instant consumedAt);
}
