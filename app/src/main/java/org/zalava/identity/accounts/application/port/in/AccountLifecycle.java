package org.zalava.identity.accounts.application.port.in;

import java.util.List;
import java.util.Optional;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

public interface AccountLifecycle {
  List<Account> list();

  Optional<Account> findByLoginName(String loginName);

  Optional<Account> findById(AccountId id);

  void bootstrap(String loginName, String password);

  Account create(String loginName, String temporaryPassword, AccountRole role);

  void changePassword(AccountId accountId, String currentPassword, String replacementPassword);

  void resetPassword(AccountId accountId, String temporaryPassword);

  void setEnabled(AccountId accountId, boolean enabled);

  void setRole(AccountId accountId, AccountRole role);
}
