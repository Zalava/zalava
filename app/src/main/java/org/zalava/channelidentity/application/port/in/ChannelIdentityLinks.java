package org.zalava.channelidentity.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.zalava.accounts.domain.Actor;
import org.zalava.channelidentity.domain.ChannelIdentityLink;
import org.zalava.channelidentity.domain.ChannelOperationScope;
import org.zalava.channelidentity.domain.ExternalChannelIdentity;

/** SEA-owned authority boundary used by channel ingress before any work starts. */
public interface ChannelIdentityLinks {
  ChannelIdentityLink link(
      Actor owner, ExternalChannelIdentity identity, ChannelOperationScope scope);

  Optional<Actor> resolve(ExternalChannelIdentity identity, String operation);

  List<ChannelIdentityLink> links(Actor owner);

  void revoke(Actor owner, UUID linkId);
}
