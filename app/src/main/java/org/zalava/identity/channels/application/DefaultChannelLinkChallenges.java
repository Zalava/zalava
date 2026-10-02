package org.zalava.identity.channels.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.application.port.in.ChannelLinkChallenges;
import org.zalava.identity.channels.application.port.out.ChannelLinkChallengeStore;
import org.zalava.identity.channels.domain.ChannelIdentityLink;
import org.zalava.identity.channels.domain.ChannelLinkChallenge;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;

public final class DefaultChannelLinkChallenges implements ChannelLinkChallenges {
  private static final Duration LIFETIME = Duration.ofMinutes(10);
  private final AccountLifecycle accounts;
  private final ChannelIdentityLinks links;
  private final ChannelLinkChallengeStore challenges;
  private final Clock clock;
  private final Supplier<String> codes;

  public DefaultChannelLinkChallenges(
      AccountLifecycle accounts,
      ChannelIdentityLinks links,
      ChannelLinkChallengeStore challenges,
      Clock clock) {
    this(accounts, links, challenges, clock, DefaultChannelLinkChallenges::newCode);
  }

  DefaultChannelLinkChallenges(
      AccountLifecycle accounts,
      ChannelIdentityLinks links,
      ChannelLinkChallengeStore challenges,
      Clock clock,
      Supplier<String> codes) {
    this.accounts = Objects.requireNonNull(accounts);
    this.links = Objects.requireNonNull(links);
    this.challenges = Objects.requireNonNull(challenges);
    this.clock = Objects.requireNonNull(clock);
    this.codes = Objects.requireNonNull(codes);
  }

  @Override
  public IssuedChannelLinkChallenge issue(
      Actor owner, String channel, ChannelOperationScope scope) {
    requireEnabled(owner);
    var now = clock.instant();
    var code = codes.get();
    var normalizedChannel = new ExternalChannelIdentity(channel, "subject").channel();
    var challenge =
        new ChannelLinkChallenge(
            UUID.randomUUID(),
            owner,
            normalizedChannel,
            scope,
            hash(code),
            now.plus(LIFETIME),
            null);
    challenges.save(challenge);
    return new IssuedChannelLinkChallenge(code, challenge.expiresAt());
  }

  @Override
  public ChannelIdentityLink confirm(ExternalChannelIdentity identity, String code) {
    var now = clock.instant();
    var challenge =
        challenges
            .findActiveByCodeHash(hash(code), now)
            .filter(value -> value.channel().equals(identity.channel()))
            .orElseThrow(
                () -> new IllegalArgumentException("Channel link code is invalid or expired"));
    requireEnabled(challenge.owner());
    if (!challenges.consume(challenge.id(), now))
      throw new IllegalArgumentException("Channel link code is invalid or expired");
    return links.link(challenge.owner(), identity, challenge.scope());
  }

  private void requireEnabled(Actor actor) {
    if (accounts.findById(actor.accountId()).filter(account -> account.enabled()).isEmpty())
      throw new IllegalArgumentException("Channel links require an enabled account");
  }

  private static String newCode() {
    var bytes = new byte[24];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String hash(String code) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(Objects.requireNonNull(code).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
