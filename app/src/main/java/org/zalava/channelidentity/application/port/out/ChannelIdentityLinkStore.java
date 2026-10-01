package org.zalava.channelidentity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.zalava.accounts.domain.Actor;
import org.zalava.channelidentity.domain.ChannelIdentityLink;
import org.zalava.channelidentity.domain.ExternalChannelIdentity;

/** Storage boundary for inspectable, revocable channel identity links. */
public interface ChannelIdentityLinkStore {
  Optional<ChannelIdentityLink> findActive(ExternalChannelIdentity identity);

  Optional<ChannelIdentityLink> findById(UUID id);

  List<ChannelIdentityLink> findByOwner(Actor owner);

  ChannelIdentityLink save(ChannelIdentityLink link);
}
