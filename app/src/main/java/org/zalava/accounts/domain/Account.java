package org.zalava.accounts.domain;

import java.time.Instant;
import java.util.Objects;

public record Account(
    AccountId id,
    String loginName,
    String passwordHash,
    boolean enabled,
    AccountRole role,
    boolean passwordChangeRequired,
    Instant createdAt,
    Instant updatedAt,
    long version) {
  public Account {
    Objects.requireNonNull(id, "id");
    loginName = normalizeLogin(loginName);
    Objects.requireNonNull(passwordHash, "passwordHash");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (version < 0) throw new IllegalArgumentException("version must not be negative");
  }

  public Account withVersion(long version) {
    return new Account(
        id,
        loginName,
        passwordHash,
        enabled,
        role,
        passwordChangeRequired,
        createdAt,
        updatedAt,
        version);
  }

  public static String normalizeLogin(String value) {
    if (value == null || value.isBlank() || value.length() > 100)
      throw new IllegalArgumentException("Login name must contain between 1 and 100 characters");
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,99}"))
      throw new IllegalArgumentException("Login name contains unsupported characters");
    return normalized;
  }
}
