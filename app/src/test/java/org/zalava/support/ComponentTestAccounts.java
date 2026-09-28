package org.zalava.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;

/** Creates activated SEA accounts and matching MockMvc identities for component tests. */
public final class ComponentTestAccounts {

  private static final AtomicInteger LOGINS = new AtomicInteger();
  private static final String TEMPORARY_PASSWORD = "TemporaryPassword-123";
  private static final String PERMANENT_PASSWORD = "PermanentPassword-123";

  private final AccountLifecycle accounts;

  public ComponentTestAccounts(AccountLifecycle accounts) {
    this.accounts = accounts;
  }

  public Account activated(String login, AccountRole role) {
    Account account = accounts.findByLoginName(login).orElseGet(() -> createOrFind(login, role));
    if (account.passwordChangeRequired()) {
      accounts.changePassword(account.id(), TEMPORARY_PASSWORD, PERMANENT_PASSWORD);
    }
    return accounts.findByLoginName(login).orElseThrow();
  }

  private Account createOrFind(String login, AccountRole role) {
    try {
      return accounts.create(login, TEMPORARY_PASSWORD, role);
    } catch (IllegalArgumentException race) {
      return accounts.findByLoginName(login).orElseThrow(() -> race);
    }
  }

  public Account newActivated(AccountRole role) {
    return activated(
        "component-" + role.name().toLowerCase() + "-" + LOGINS.incrementAndGet(), role);
  }

  public RequestPostProcessor authenticatedAs(Account account) {
    return user(account.loginName()).roles(account.role().name());
  }
}
