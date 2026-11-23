package org.zalava.capabilities.operation.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zalava.api.InvocationContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

class HomeAssistantActionPolicyTest {
  private final AccountLifecycle accounts = mock(AccountLifecycle.class);
  private final UUID id = UUID.randomUUID();
  private final HomeAssistantActionPolicy policy = new HomeAssistantActionPolicy(accounts);
  private final InvocationContext actor =
      new InvocationContext(
          id.toString(),
          false,
          Map.of(
              "accountRole", "MEMBER", "permissionPolicyChannel", "web", "conversationId", "one"));

  private void account(boolean enabled, AccountRole role, boolean passwordChange) {
    when(accounts.findById(new AccountId(id)))
        .thenReturn(
            Optional.of(
                new Account(
                    new AccountId(id),
                    "test",
                    "hash",
                    enabled,
                    role,
                    passwordChange,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    0)));
  }

  private Map<String, Object> action() {
    return new HashMap<>(
        Map.of(
            "domain",
            "light",
            "action",
            "turn_on",
            "entityId",
            "light.room",
            "serviceData",
            Map.of("brightness", 30)));
  }

  @Test
  void acceptsCurrentActorAndExplicitScopedAction() {
    account(true, AccountRole.MEMBER, false);
    assertThatCode(() -> policy.require(actor, action())).doesNotThrowAnyException();
  }

  @Test
  void deniesStaleDisabledMissingAndTemporaryAccounts() {
    for (var accountState :
        List.of(
            new Object[] {false, AccountRole.MEMBER, false},
            new Object[] {true, AccountRole.ADMIN, false},
            new Object[] {true, AccountRole.MEMBER, true})) {
      account((boolean) accountState[0], (AccountRole) accountState[1], (boolean) accountState[2]);
      assertThatThrownBy(() -> policy.require(actor, action())).hasMessageContaining("denied");
    }
    when(accounts.findById(new AccountId(id))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> policy.require(actor, action())).hasMessageContaining("denied");
  }

  @Test
  void deniesIndirectBulkUnknownAndMismatchedActions() {
    account(true, AccountRole.MEMBER, false);
    for (var change :
        List.of(
            Map.<String, Object>of("domain", "script"),
            Map.<String, Object>of("action", "unlock"),
            Map.<String, Object>of("entityId", "all"),
            Map.<String, Object>of("entityId", "switch.room"),
            Map.<String, Object>of("serviceData", Map.of("entity_id", "light.other")),
            Map.<String, Object>of(
                "serviceData", Map.of("nested", Map.of("entity_id", "light.other"))),
            Map.<String, Object>of("url", "http://example.com"))) {
      var args = action();
      args.putAll(change);
      assertThatThrownBy(() -> policy.require(actor, args)).hasMessageContaining("denied");
    }
  }
}
