package org.zalava.channelidentity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.channelidentity.application.port.out.ChannelIdentityLinkStore;
import org.zalava.channelidentity.domain.ChannelIdentityLink;
import org.zalava.channelidentity.domain.ChannelOperationScope;
import org.zalava.channelidentity.domain.ExternalChannelIdentity;

class DefaultChannelIdentityLinksTest {
  private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void resolvesOnlyAnEnabledOwnerWithTheRequestedScope() {
    var accounts = new Accounts();
    var owner = accounts.enabled("owner");
    var identities = new DefaultChannelIdentityLinks(accounts, new Links(), CLOCK);
    var identity = new ExternalChannelIdentity("telegram", "123456789");

    identities.link(new Actor(owner.id()), identity, ChannelOperationScope.of("chat:send"));

    assertThat(identities.resolve(identity, "chat:send")).contains(new Actor(owner.id()));
    assertThat(identities.resolve(identity, "provider:execute")).isEmpty();

    accounts.disable(owner.id());

    assertThat(identities.resolve(identity, "chat:send")).isEmpty();
  }

  @Test
  void rejectsConflictingLinksAndMakesOwnerRevocationImmediate() {
    var accounts = new Accounts();
    var owner = accounts.enabled("owner");
    var other = accounts.enabled("other");
    var identities = new DefaultChannelIdentityLinks(accounts, new Links(), CLOCK);
    var identity = new ExternalChannelIdentity("telegram", "123456789");
    var link =
        identities.link(new Actor(owner.id()), identity, ChannelOperationScope.of("chat:send"));

    assertThatThrownBy(
            () ->
                identities.link(
                    new Actor(other.id()), identity, ChannelOperationScope.of("chat:send")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already linked");
    assertThatThrownBy(() -> identities.revoke(new Actor(other.id()), link.id()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not own");

    identities.revoke(new Actor(owner.id()), link.id());

    assertThat(identities.resolve(identity, "chat:send")).isEmpty();
  }

  private static final class Accounts implements AccountLifecycle {
    private final Map<AccountId, Account> accounts = new HashMap<>();

    Account enabled(String login) {
      var account =
          new Account(
              AccountId.newId(), login, "hash", true, AccountRole.MEMBER, false, NOW, NOW, 0);
      accounts.put(account.id(), account);
      return account;
    }

    void disable(AccountId id) {
      var account = accounts.get(id);
      accounts.put(
          id,
          new Account(
              account.id(),
              account.loginName(),
              account.passwordHash(),
              false,
              account.role(),
              account.passwordChangeRequired(),
              account.createdAt(),
              NOW,
              account.version()));
    }

    @Override
    public Optional<Account> findById(AccountId id) {
      return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public java.util.List<Account> list() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> findByLoginName(String loginName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void bootstrap(String loginName, String password) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Account create(String loginName, String temporaryPassword, AccountRole role) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void changePassword(
        AccountId accountId, String currentPassword, String replacementPassword) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void resetPassword(AccountId accountId, String temporaryPassword) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setEnabled(AccountId accountId, boolean enabled) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setRole(AccountId accountId, AccountRole role) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Links implements ChannelIdentityLinkStore {
    private final Map<java.util.UUID, ChannelIdentityLink> links = new HashMap<>();

    @Override
    public Optional<ChannelIdentityLink> findActive(ExternalChannelIdentity identity) {
      return links.values().stream()
          .filter(link -> link.identity().equals(identity) && link.revokedAt() == null)
          .findFirst();
    }

    @Override
    public Optional<ChannelIdentityLink> findById(java.util.UUID id) {
      return Optional.ofNullable(links.get(id));
    }

    @Override
    public ChannelIdentityLink save(ChannelIdentityLink link) {
      links.put(link.id(), link);
      return link;
    }
  }
}
