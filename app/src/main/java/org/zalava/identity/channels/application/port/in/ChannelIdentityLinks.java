package org.zalava.identity.channels.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.domain.ChannelIdentityLink;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;

/** Zalava-owned authority boundary used by channel ingress before any work starts. */
public interface ChannelIdentityLinks {
  ChannelIdentityLink link(
      Actor owner, ExternalChannelIdentity identity, ChannelOperationScope scope);

  Optional<Actor> resolve(ExternalChannelIdentity identity, String operation);

  List<ChannelIdentityLink> links(Actor owner);

  void revoke(Actor owner, UUID linkId);
}
