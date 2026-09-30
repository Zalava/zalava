package org.zalava.accounts.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.application.port.out.AccountStore;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;

public class AccountLifecycleService implements AccountLifecycle {
  private final AccountStore accounts;
  private final PasswordEncoder passwords;
  private final Clock clock;

  public AccountLifecycleService(AccountStore accounts, PasswordEncoder passwords, Clock clock) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.clock = clock;
  }

  @Override
  public List<Account> list() {
    return accounts.findAll();
  }

  @Override
  public Optional<Account> findByLoginName(String loginName) {
    return accounts.findByLoginName(Account.normalizeLogin(loginName));
  }

  @Override
  public Optional<Account> findById(AccountId id) {
    return accounts.findById(id);
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void bootstrap(String loginName, String password) {
    if (accounts.enabledAdministratorCount() != 0) return;
    Instant now = clock.instant();
    Account account =
        new Account(
            AccountId.newId(),
            loginName,
            encoded(password),
            true,
            AccountRole.ADMIN,
            true,
            now,
            now,
            0);
    accounts.create(account);
  }

  @Override
  @Transactional
  public Account create(String loginName, String temporaryPassword, AccountRole role) {
    if (accounts.findByLoginName(Account.normalizeLogin(loginName)).isPresent())
      throw new IllegalArgumentException("Login name is already in use");
    Instant now = clock.instant();
    Account account =
        new Account(
            AccountId.newId(),
            loginName,
            encoded(temporaryPassword),
            true,
            role,
            true,
            now,
            now,
            0);
    accounts.create(account);
    return account;
  }

  @Override
  @Transactional
  public void changePassword(AccountId id, String currentPassword, String replacementPassword) {
    Account account = required(id);
    if (!passwords.matches(requiredPassword(currentPassword), account.passwordHash()))
      throw new IllegalArgumentException("Current password is invalid");
    accounts.save(
        copy(account, encoded(replacementPassword), account.enabled(), account.role(), false));
  }

  @Override
  @Transactional
  public void resetPassword(AccountId id, String temporaryPassword) {
    Account account = required(id);
    accounts.save(
        copy(account, encoded(temporaryPassword), account.enabled(), account.role(), true));
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void setEnabled(AccountId id, boolean enabled) {
    Account account = required(id);
    requireAdministratorRemains(account, enabled, account.role());
    accounts.save(
        copy(
            account,
            account.passwordHash(),
            enabled,
            account.role(),
            account.passwordChangeRequired()));
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void setRole(AccountId id, AccountRole role) {
    Account account = required(id);
    requireAdministratorRemains(account, account.enabled(), role);
    accounts.save(
        copy(
            account,
            account.passwordHash(),
            account.enabled(),
            role,
            account.passwordChangeRequired()));
  }

  private Account required(AccountId id) {
    return accounts
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Account does not exist"));
  }

  private void requireAdministratorRemains(Account account, boolean enabled, AccountRole role) {
    if (account.enabled()
        && account.role() == AccountRole.ADMIN
        && (!enabled || role != AccountRole.ADMIN)
        && accounts.enabledAdministratorCount() <= 1)
      throw new IllegalStateException("At least one enabled administrator is required");
  }

  private Account copy(
      Account account,
      String hash,
      boolean enabled,
      AccountRole role,
      boolean passwordChangeRequired) {
    return new Account(
        account.id(),
        account.loginName(),
        hash,
        enabled,
        role,
        passwordChangeRequired,
        account.createdAt(),
        clock.instant(),
        account.version());
  }

  private String encoded(String value) {
    return passwords.encode(requiredPassword(value));
  }

  private static String requiredPassword(String value) {
    if (value == null || value.length() < 12 || value.length() > 256)
      throw new IllegalArgumentException("Password must contain between 12 and 256 characters");
    return value;
  }
}
