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
  public List<ToolChoice> availableTools(String administratorId) {
    requireAdministrator(administratorId);
    return catalog.providers().stream()
        .flatMap(
            provider ->
                provider.listTools().stream()
                    .map(
                        tool ->
                            new ToolChoice(
                                new ToolKey(
                                    provider.descriptor().moduleId(),
                                    provider.descriptor().providerId(),
                                    tool.name()),
                                provider.descriptor().displayName(),
                                tool.description(),
                                MemberProviderCapabilityPolicy.eligible(provider, tool))))
        .toList();
  }

  @Override
  public boolean allowed(String memberId, ToolKey key) {
    try {
      return accounts
              .findById(id(memberId))
              .filter(account -> account.enabled() && account.role() == AccountRole.MEMBER)
              .isPresent()
          && store.decisions(memberId).stream()
              .filter(value -> value.key().equals(key))
              .map(Decision::allowed)
              .findFirst()
              .orElseGet(() -> store.defaultAllowed(key));
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
    validateAvailable(key, allowed);
    store.decide(administratorId, memberId, key, allowed, clock.instant());
  }

  private void validateAvailable(ToolKey key, boolean allowed) {
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
  }

  @Override
  public List<Decision> defaults(String administratorId) {
    requireAdministrator(administratorId);
    return store.defaults();
  }

  @Override
  public void defaultAccess(String administratorId, ToolKey key, boolean membersAllowed) {
    requireAdministrator(administratorId);
    validateAvailable(key, membersAllowed);
    store.defaultAccess(administratorId, key, membersAllowed, clock.instant());
  }

  @Override
  public void inherit(String administratorId, String memberId, ToolKey key) {
    requireAdministrator(administratorId);
    requireMember(memberId);
    store.inherit(administratorId, memberId, key, clock.instant());
  }

  @Override
  public Effective effective(String administratorId, String memberId, ToolKey key) {
    requireAdministrator(administratorId);
    requireMember(memberId);
    var override =
        store.decisions(memberId).stream().filter(value -> value.key().equals(key)).findFirst();
    boolean roleAllowed = store.defaultAllowed(key);
    return new Effective(
        override.map(Decision::allowed).orElse(roleAllowed),
        override.isPresent() ? "User override" : "Role default",
        override.map(value -> value.allowed() ? "ALLOW" : "DENY").orElse("INHERIT"),
        roleAllowed);
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
