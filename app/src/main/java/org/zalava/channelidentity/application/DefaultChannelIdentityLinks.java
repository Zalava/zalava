package org.zalava.channelidentity.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Actor;
import org.zalava.channelidentity.application.port.in.ChannelIdentityLinks;
import org.zalava.channelidentity.application.port.out.ChannelIdentityLinkStore;
import org.zalava.channelidentity.domain.ChannelIdentityLink;
import org.zalava.channelidentity.domain.ChannelOperationScope;
import org.zalava.channelidentity.domain.ExternalChannelIdentity;

public final class DefaultChannelIdentityLinks implements ChannelIdentityLinks {
  private final AccountLifecycle accounts;
  private final ChannelIdentityLinkStore links;
  private final Clock clock;

  public DefaultChannelIdentityLinks(
      AccountLifecycle accounts, ChannelIdentityLinkStore links, Clock clock) {
    this.accounts = Objects.requireNonNull(accounts);
    this.links = Objects.requireNonNull(links);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public ChannelIdentityLink link(
      Actor owner, ExternalChannelIdentity identity, ChannelOperationScope scope) {
    requireEnabled(owner);
    if (links.findActive(identity).isPresent()) {
      throw new IllegalStateException("External channel identity is already linked");
    }
    return links.save(
        new ChannelIdentityLink(UUID.randomUUID(), identity, owner, scope, clock.instant(), null));
  }

  @Override
  public Optional<Actor> resolve(ExternalChannelIdentity identity, String operation) {
    if (operation == null || operation.isBlank()) {
      return Optional.empty();
    }
    return links
        .findActive(identity)
        .filter(link -> link.scope().allows(operation))
        .filter(link -> isEnabled(link.owner()))
        .map(ChannelIdentityLink::owner);
  }

  @Override
  public List<ChannelIdentityLink> links(Actor owner) {
    requireEnabled(owner);
    return links.findByOwner(owner);
  }

  @Override
  public void revoke(Actor owner, UUID linkId) {
    var link =
        links
            .findById(linkId)
            .orElseThrow(() -> new IllegalArgumentException("Channel link does not exist"));
    if (!link.owner().equals(owner)) {
      throw new IllegalArgumentException("Actor does not own this channel link");
    }
    if (link.revokedAt() == null) {
      links.save(link.revoke(clock.instant()));
    }
  }

  private void requireEnabled(Actor actor) {
    if (!isEnabled(actor)) {
      throw new IllegalArgumentException("Channel links require an enabled account");
    }
  }

  private boolean isEnabled(Actor actor) {
    return accounts.findById(actor.accountId()).map(account -> account.enabled()).orElse(false);
  }
}
