package org.zalava.identity.accounts.domain;

import java.util.Objects;

/** A Zalava-created authority for one account's private state. */
public record Actor(AccountId accountId) {
  public Actor {
    Objects.requireNonNull(accountId, "accountId");
  }
}
