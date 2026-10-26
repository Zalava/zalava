package org.zalava.capabilities.operation.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.out.MemberToolAccessStore;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

public final class DefaultMemberToolAccess implements MemberToolAccess {
  private final AccountLifecycle accounts;
  private final MemberToolAccessStore store;
  private final ProviderCatalog catalog;
  private final Clock clock;

  public DefaultMemberToolAccess(
      AccountLifecycle accounts,
      MemberToolAccessStore store,
      ProviderCatalog catalog,
      Clock clock) {
    this.accounts = accounts;
    this.store = store;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Override
  public boolean allowed(String memberId, ToolKey key) {
    try {
      return accounts
              .findById(id(memberId))
              .filter(account -> account.enabled() && account.role() == AccountRole.MEMBER)
              .isPresent()
          && store.allowed(memberId, key);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  @Override
  public List<Decision> decisions(String administratorId, String memberId) {
    requireAdministrator(administratorId);
    requireMember(memberId);
    return store.decisions(memberId);
  }

  @Override
  public void decide(String administratorId, String memberId, ToolKey key, boolean allowed) {
    requireAdministrator(administratorId);
    requireMember(memberId);
    if (allowed) {
      var provider =
          catalog
              .findProvider(key.providerId())
              .filter(value -> value.descriptor().moduleId().equals(key.moduleId()))
              .orElseThrow(() -> new IllegalArgumentException("Tool is not currently available"));
      var tool =
          provider.listTools().stream()
              .filter(value -> value.name().equals(key.toolName()))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Tool is not currently available"));
      if (!MemberProviderCapabilityPolicy.eligible(provider, tool))
        throw new IllegalArgumentException("This tool is restricted by mandatory host policy");
    }
    store.decide(administratorId, memberId, key, allowed, clock.instant());
  }

  private void requireAdministrator(String administratorId) {
    if (accounts
        .findById(id(administratorId))
        .filter(account -> account.enabled() && account.role() == AccountRole.ADMIN)
        .isEmpty()) throw new SecurityException("Administrator access is required");
  }

  private void requireMember(String memberId) {
    if (accounts
        .findById(id(memberId))
        .filter(account -> account.role() == AccountRole.MEMBER)
        .isEmpty()) throw new IllegalArgumentException("Choose an existing Member account");
  }

  private static AccountId id(String value) {
    return new AccountId(UUID.fromString(value));
  }
}
