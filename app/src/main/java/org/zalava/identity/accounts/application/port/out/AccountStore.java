package org.zalava.identity.accounts.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;

public interface AccountStore {
  List<Account> findAll();

  Optional<Account> findByLoginName(String loginName);

  Optional<Account> findById(AccountId id);

  long enabledAdministratorCount();

  Account create(Account account);

  Account save(Account account);
}
