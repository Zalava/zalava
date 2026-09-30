package org.zalava.channelidentity.application.port.in;

import java.time.Instant;
import org.zalava.accounts.domain.Actor;
import org.zalava.channelidentity.domain.ChannelIdentityLink;
import org.zalava.channelidentity.domain.ChannelOperationScope;
import org.zalava.channelidentity.domain.ExternalChannelIdentity;

public interface ChannelLinkChallenges {
  IssuedChannelLinkChallenge issue(Actor owner, String channel, ChannelOperationScope scope);

  ChannelIdentityLink confirm(ExternalChannelIdentity identity, String code);

  record IssuedChannelLinkChallenge(String code, Instant expiresAt) {}
}
