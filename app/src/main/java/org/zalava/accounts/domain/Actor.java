package org.zalava.accounts.domain;

import java.util.Objects;

/** A SEA-created authority for one account's private state. */
public record Actor(AccountId accountId) {
  public Actor {
    Objects.requireNonNull(accountId, "accountId");
  }
}
