package org.zalava.accounts.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AuthenticatedActorResolverTest {
  private final AccountLifecycle accounts = Mockito.mock(AccountLifecycle.class);
  private final AuthenticatedActorResolver resolver = new AuthenticatedActorResolver(accounts);

  @Test
  void resolvesThePersistedEnabledAccountInsteadOfAClientSuppliedIdentity() {
    AccountId accountId = AccountId.newId();
    when(accounts.findByLoginName("member"))
        .thenReturn(Optional.of(account(accountId, "member", true)));

    var actor = resolver.actor(authenticated("member"));

    assertThat(actor.accountId()).isEqualTo(accountId);
  }

  @Test
  void rejectsMissingOrDisabledAccounts() {
    assertThatThrownBy(() -> resolver.actor(null)).isInstanceOf(AccessDeniedException.class);
    when(accounts.findByLoginName("member"))
        .thenReturn(Optional.of(account(AccountId.newId(), "member", false)));

    assertThatThrownBy(() -> resolver.actor(authenticated("member")))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static Account account(AccountId id, String login, boolean enabled) {
    return new Account(
        id, login, "hash", enabled, AccountRole.MEMBER, false, Instant.EPOCH, Instant.EPOCH, 0);
  }

  private static UsernamePasswordAuthenticationToken authenticated(String login) {
    return new UsernamePasswordAuthenticationToken(login, "ignored", List.of());
  }
}
