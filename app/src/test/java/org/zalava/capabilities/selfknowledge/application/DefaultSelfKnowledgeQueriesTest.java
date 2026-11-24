package org.zalava.capabilities.selfknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.RuntimeState;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

class DefaultSelfKnowledgeQueriesTest {
  @Test
  void pageOnlyModuleIsVisibleOnlyWhilePageAccessIsGranted() {
    var actors = new ActorExecutionContext();
    var accounts = mock(AccountLifecycle.class);
    var runtime = mock(RuntimeQueries.class);
    var routes = mock(WebExtensionRoutes.class);
    var pages = mock(ModulePageAccess.class);
    var account = mock(Account.class);
    var id = AccountId.newId();
    when(accounts.findById(id)).thenReturn(Optional.of(account));
    when(account.enabled()).thenReturn(true);
    when(account.id()).thenReturn(id);
    when(account.role()).thenReturn(AccountRole.MEMBER);
    var module = mock(ZalavaModule.class);
    when(module.descriptor()).thenReturn(new ModuleDescriptor("notes", "1", "Notes", "Notes"));
    when(runtime.modules()).thenReturn(List.of(module));
    when(runtime.activeModules()).thenReturn(List.of(module));
    when(routes.pages())
        .thenReturn(
            List.of(
                new RegisteredWebPage(
                    "notes", "Notes", "main", "list", "List", "Notes list", "apps", List.of())));
    when(pages.allowed(id.toString(), "notes", "list")).thenReturn(true, false);
    var queries =
        new DefaultSelfKnowledgeQueries(
            actors,
            accounts,
            runtime,
            mock(ProviderCatalog.class),
            mock(MemberToolVisibility.class),
            routes,
            pages,
            ignored -> RuntimeState.STOPPED,
            mock(ModuleQueries.class));
    var actor = new Actor(id);

    var visible = actors.call(actor, AccountRole.MEMBER, () -> queries.find("module:notes"));
    assertThat(visible).isPresent();
    assertThat(visible.orElseThrow().pages())
        .extracting("path")
        .containsExactly("/apps/notes/list");
    assertThat(actors.call(actor, AccountRole.MEMBER, () -> queries.find("module:notes")))
        .isEmpty();
  }

  @Test
  void queriesFreshAccessAndRuntimeWithoutGrantingFromMetadata() {
    var actors = new ActorExecutionContext();
    var accounts = mock(AccountLifecycle.class);
    var runtime = mock(RuntimeQueries.class);
    var catalog = mock(ProviderCatalog.class);
    var access = mock(MemberToolVisibility.class);
    var account = mock(Account.class);
    var id = AccountId.newId();
    var actor = new Actor(id);
    when(accounts.findById(id)).thenReturn(Optional.of(account));
    when(account.id()).thenReturn(id);
    when(account.enabled()).thenReturn(true);
    when(account.role()).thenReturn(AccountRole.MEMBER);
    var module = mock(ZalavaModule.class);
    when(module.descriptor()).thenReturn(new ModuleDescriptor("notes", "1", "Notes", "Notes"));
    when(runtime.modules()).thenReturn(List.of(module));
    when(runtime.activeModules()).thenReturn(List.of(module), List.of());
    var provider = mock(ZalavaProvider.class);
    when(provider.descriptor())
        .thenReturn(
            new ProviderDescriptor(
                "notes",
                "notes",
                "notes",
                "Notes",
                "Notes",
                "1",
                ProviderCapabilities.toolsOnly(),
                List.of("zalava_backed"),
                Map.of("owner", "self")));
    when(provider.listTools()).thenReturn(List.of(new ZalavaToolDescriptor("read", "Read", false)));
    when(catalog.providers()).thenReturn(List.of(provider));
    var tool = provider.listTools().getFirst();
    when(access.allowed(id.toString(), provider, tool)).thenReturn(true, false);
    var queries =
        new DefaultSelfKnowledgeQueries(
            actors,
            accounts,
            runtime,
            catalog,
            access,
            mock(WebExtensionRoutes.class),
            mock(ModulePageAccess.class),
            ignored -> RuntimeState.STOPPED,
            mock(ModuleQueries.class));

    var first = actors.call(actor, AccountRole.MEMBER, () -> queries.find("module:notes"));
    assertThat(first).isPresent();
    assertThat(first.orElseThrow().runtimeState()).isEqualTo(RuntimeState.ACTIVE);
    assertThat(first.orElseThrow().operations()).hasSize(1);
    assertThat(actors.call(actor, AccountRole.MEMBER, () -> queries.find("module:notes")))
        .isEmpty();
  }

  @Test
  void rejectsMissingOrDisabledAccountsAndInvalidBounds() {
    var actors = new ActorExecutionContext();
    var accounts = mock(AccountLifecycle.class);
    var queries =
        new DefaultSelfKnowledgeQueries(
            actors,
            accounts,
            mock(RuntimeQueries.class),
            mock(ProviderCatalog.class),
            mock(MemberToolVisibility.class),
            mock(WebExtensionRoutes.class),
            mock(ModulePageAccess.class),
            ignored -> RuntimeState.STOPPED,
            mock(ModuleQueries.class));
    assertThatThrownBy(() -> queries.search("", 1)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> queries.search("", 51)).isInstanceOf(IllegalArgumentException.class);
    var id = AccountId.newId();
    var account = mock(Account.class);
    when(accounts.findById(id)).thenReturn(Optional.of(account));
    when(account.enabled()).thenReturn(false);
    assertThatThrownBy(
            () -> actors.call(new Actor(id), AccountRole.ADMIN, () -> queries.search("", 1)))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void administratorSeesInactiveModuleButNotAClaimOfReadiness() {
    var actors = new ActorExecutionContext();
    var accounts = mock(AccountLifecycle.class);
    var runtime = mock(RuntimeQueries.class);
    var catalog = mock(ProviderCatalog.class);
    var installed = mock(ModuleQueries.class);
    var account = mock(Account.class);
    var id = AccountId.newId();
    when(accounts.findById(id)).thenReturn(Optional.of(account));
    when(account.enabled()).thenReturn(true);
    when(account.role()).thenReturn(AccountRole.ADMIN);
    var module = mock(ZalavaModule.class);
    when(module.descriptor()).thenReturn(new ModuleDescriptor("notes", "1", "Notes", "Notes"));
    when(runtime.modules()).thenReturn(List.of(module));
    when(runtime.activeModules()).thenReturn(List.of());
    when(catalog.providers()).thenReturn(List.of());
    when(installed.enabledModules())
        .thenReturn(List.of(new ModuleQueries.EnabledModule("queued", "2", "", "", List.of())));
    var queries =
        new DefaultSelfKnowledgeQueries(
            actors,
            accounts,
            runtime,
            catalog,
            mock(MemberToolVisibility.class),
            mock(WebExtensionRoutes.class),
            mock(ModulePageAccess.class),
            ignored -> RuntimeState.SETUP_REQUIRED,
            installed);

    var result = actors.call(new Actor(id), AccountRole.ADMIN, () -> queries.find("module:notes"));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().runtimeState()).isEqualTo(RuntimeState.SETUP_REQUIRED);
    assertThat(result.orElseThrow().readiness().name()).isEqualTo("UNKNOWN");
    assertThat(result.orElseThrow().operations()).isEmpty();
    var queued = actors.call(new Actor(id), AccountRole.ADMIN, () -> queries.find("module:queued"));
    assertThat(queued).isPresent();
    assertThat(queued.orElseThrow().runtimeState()).isEqualTo(RuntimeState.NOT_LOADED);
  }
}
