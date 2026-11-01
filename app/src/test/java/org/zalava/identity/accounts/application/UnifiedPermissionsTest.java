package org.zalava.identity.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.RegisteredWebRoute;
import org.zalava.modules.web.application.port.in.ModulePageAccess;

class UnifiedPermissionsTest {
  @Test
  void personPageAccessComesFromTheAuthoritativePolicy() {
    var tools = mock(MemberToolAccess.class);
    var confirmation = mock(ChatToolConfirmation.class);
    var pages = mock(ModulePageAccess.class);
    var accounts = mock(AccountLifecycle.class);
    var now = Instant.now();
    var person =
        new Account(
            new AccountId(UUID.randomUUID()),
            "member",
            "hash",
            true,
            AccountRole.MEMBER,
            false,
            now,
            now,
            0);
    when(accounts.list()).thenReturn(List.of(person));
    var page =
        new RegisteredWebPage(
            "shopping",
            "Shopping",
            "list",
            "items",
            "Items",
            "",
            "apps",
            List.of(mock(RegisteredWebRoute.class)));
    when(pages.pages("admin")).thenReturn(List.of(page));
    when(pages.memberDefault("admin", "shopping", "items")).thenReturn(true);
    when(pages.override("admin", person.id().toString(), "shopping", "items")).thenReturn("ALLOW");
    when(pages.allowed(person.id().toString(), "shopping", "items")).thenReturn(false);

    var snapshot =
        new UnifiedPermissions(tools, confirmation, pages, accounts)
            .snapshot("admin", person.id().toString());

    assertThat(snapshot.appRows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.choice()).isEqualTo("ALLOW");
              assertThat(row.allowed()).isFalse();
            });
    verify(pages).allowed(person.id().toString(), "shopping", "items");
    verify(tools, times(1)).defaults("admin");
  }
}
