package org.zalava.identity.accounts.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.zalava.identity.accounts.application.port.out.AccountStore;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

class AccountLifecycleServiceTest {
  private final InMemoryStore store = new InMemoryStore();
  private final AccountLifecycleService accounts =
      new AccountLifecycleService(
          store,
          new BCryptPasswordEncoder(4),
          Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void retainsAtLeastOneEnabledAdministratorWhileAllowingSeveral() {
    accounts.bootstrap("admin-one", "BootstrapPassword-123");
    Account first = store.findByLoginName("admin-one").orElseThrow();
    assertThatThrownBy(() -> accounts.setEnabled(first.id(), false))
        .isInstanceOf(IllegalStateException.class);
    Account second = accounts.create("admin-two", "TemporaryPassword-123", AccountRole.ADMIN);
    accounts.setEnabled(first.id(), false);
    assertThatThrownBy(() -> accounts.setRole(second.id(), AccountRole.MEMBER))
        .isInstanceOf(IllegalStateException.class);
  }

  private static final class InMemoryStore implements AccountStore {
    private final Map<AccountId, Account> values = new HashMap<>();

    @Override
    public List<Account> findAll() {
      return List.copyOf(values.values());
    }

    @Override
    public Optional<Account> findByLoginName(String name) {
      return values.values().stream()
          .filter(account -> account.loginName().equals(name))
          .findFirst();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
      return Optional.ofNullable(values.get(id));
    }

    @Override
    public long enabledAdministratorCount() {
      return values.values().stream()
          .filter(account -> account.enabled() && account.role() == AccountRole.ADMIN)
          .count();
    }

    @Override
    public Account create(Account account) {
      values.put(account.id(), account);
      return account;
    }

    @Override
    public Account save(Account account) {
      Account saved = account.withVersion(account.version() + 1);
      values.put(saved.id(), saved);
      return saved;
    }
  }
}
