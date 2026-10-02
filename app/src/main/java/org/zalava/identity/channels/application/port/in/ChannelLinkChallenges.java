package org.zalava.identity.channels.application.port.in;

import java.time.Instant;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.domain.ChannelIdentityLink;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;

public interface ChannelLinkChallenges {
  IssuedChannelLinkChallenge issue(Actor owner, String channel, ChannelOperationScope scope);

  ChannelIdentityLink confirm(ExternalChannelIdentity identity, String code);

  record IssuedChannelLinkChallenge(String code, Instant expiresAt) {}
}
