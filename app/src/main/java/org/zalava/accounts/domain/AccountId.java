package org.zalava.accounts.domain;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {
  public AccountId {
    Objects.requireNonNull(value, "value");
  }

  public static AccountId newId() {
    return new AccountId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
