package org.zalava.capabilities.operation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.operation.application.port.out.MemberToolAccessStore;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

class DefaultMemberToolAccessTest {
  @Test
  void effectiveUseRequiresCurrentEligibleProviderAndMemberGrant() {
    var accounts = mock(AccountLifecycle.class);
    var store = mock(MemberToolAccessStore.class);
    var catalog = mock(ProviderCatalog.class);
    var id = AccountId.newId();
    var account = mock(Account.class);
    when(account.enabled()).thenReturn(true);
    when(account.role()).thenReturn(AccountRole.MEMBER);
    when(accounts.findById(id)).thenReturn(Optional.of(account));
    var provider = mock(ZalavaProvider.class);
    var tool = new ZalavaToolDescriptor("read", "Read", false);
    when(provider.descriptor())
        .thenReturn(
            new ProviderDescriptor(
                "provider",
                "module",
                "test",
                "Test",
                "Test",
                "1",
                ProviderCapabilities.toolsOnly(),
                List.of("zalava_backed"),
                Map.of("owner", "self")));
    when(provider.listTools()).thenReturn(List.of(tool));
    when(catalog.findProvider("provider")).thenReturn(Optional.of(provider));
    var key = new ToolKey("module", "provider", "read");
    when(store.defaultAllowed(key)).thenReturn(true, false);
    var access = new DefaultMemberToolAccess(accounts, store, catalog, Clock.systemUTC());

    assertThat(access.canUse(id.toString(), key)).isTrue();
    assertThat(access.canUse(id.toString(), key)).isFalse();
    assertThat(access.canUse(id.toString(), new ToolKey("other", "provider", "read"))).isFalse();
    assertThat(access.canUse(id.toString(), new ToolKey("module", "provider", "missing")))
        .isFalse();
  }
}
