package org.zalava.identity.accounts.adapter.out.transaction;

import java.time.Clock;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.zalava.identity.accounts.application.AccountLifecycleService;
import org.zalava.identity.accounts.application.port.out.AccountPasswords;
import org.zalava.identity.accounts.application.port.out.AccountStore;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

/** Applies transaction policy at the host boundary, including administrator invariants. */
public class TransactionalAccountLifecycle extends AccountLifecycleService {
  public TransactionalAccountLifecycle(
      AccountStore store, AccountPasswords passwords, Clock clock) {
    super(store, passwords, clock);
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void bootstrap(String login, String password) {
    super.bootstrap(login, password);
  }

  @Override
  @Transactional
  public Account create(String login, String password, AccountRole role) {
    return super.create(login, password, role);
  }

  @Override
  @Transactional
  public void changePassword(AccountId id, String current, String replacement) {
    super.changePassword(id, current, replacement);
  }

  @Override
  @Transactional
  public void resetPassword(AccountId id, String password) {
    super.resetPassword(id, password);
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void setEnabled(AccountId id, boolean enabled) {
    super.setEnabled(id, enabled);
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void setRole(AccountId id, AccountRole role) {
    super.setRole(id, role);
  }
}
