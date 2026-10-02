package org.zalava.identity.accounts.api;

import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;

/** Redacted account fields that may be rendered by the administrator HTML view. */
public record ManagedAccountView(
    String id,
    String loginName,
    boolean enabled,
    AccountRole role,
    boolean passwordChangeRequired) {
  static ManagedAccountView from(Account account) {
    return new ManagedAccountView(
        account.id().value().toString(),
        account.loginName(),
        account.enabled(),
        account.role(),
        account.passwordChangeRequired());
  }
}
