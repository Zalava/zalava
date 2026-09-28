package org.zalava.accounts.security;

import java.util.Optional;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Resolves the current authenticated principal to SEA's application-owned actor identity. */
@Component
public final class AuthenticatedActorResolver {
  private final AccountLifecycle accounts;

  public AuthenticatedActorResolver(AccountLifecycle accounts) {
    this.accounts = accounts;
  }

  public Actor actor(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("An authenticated account is required");
    }
    return actorForLogin(authentication.getName());
  }

  public Actor actorForLogin(String loginName) {
    return accounts
        .findByLoginName(loginName)
        .filter(account -> account.enabled())
        .map(account -> new Actor(account.id()))
        .orElseThrow(() -> new AccessDeniedException("The authenticated account is unavailable"));
  }

  public AccountRole role(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("An authenticated account is required");
    }
    return accounts
        .findByLoginName(authentication.getName())
        .filter(account -> account.enabled())
        .map(account -> account.role())
        .orElseThrow(() -> new AccessDeniedException("The authenticated account is unavailable"));
  }

  public AccountRole roleForLogin(String loginName) {
    return accounts
        .findByLoginName(loginName)
        .filter(account -> account.enabled())
        .map(account -> account.role())
        .orElseThrow(() -> new AccessDeniedException("The authenticated account is unavailable"));
  }

  public Optional<Actor> actorIfAuthenticated(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) return Optional.empty();
    return Optional.of(actor(authentication));
  }
}
