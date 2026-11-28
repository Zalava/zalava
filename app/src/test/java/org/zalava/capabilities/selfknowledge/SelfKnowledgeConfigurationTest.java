package org.zalava.capabilities.selfknowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.zalava.api.ModuleConfigurationDescriptor;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.assistant.chat.application.port.in.ChatProviderStatus;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.ConfigurationState;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceQueries;
import org.zalava.modules.runtime.ManagedZalavaRuntime;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

class SelfKnowledgeConfigurationTest {
  @TempDir Path directory;

  @Test
  void reflectsActualStoredConfigurationTransitionsWithoutValues() {
    var actors = new ActorExecutionContext();
    var accounts = mock(AccountLifecycle.class);
    var id = AccountId.newId();
    var account = mock(Account.class);
    when(accounts.findById(id)).thenReturn(Optional.of(account));
    when(account.id()).thenReturn(id);
    when(account.enabled()).thenReturn(true);
    when(account.role()).thenReturn(AccountRole.ADMIN);
    var module = mock(ZalavaModule.class);
    when(module.descriptor()).thenReturn(new ModuleDescriptor("notes", "1", "Notes", "Notes"));
    when(module.configuration())
        .thenReturn(
            new ModuleConfigurationDescriptor(
                Map.of("properties", Map.of("provider", Map.of("type", "object")))));
    var runtime = mock(ManagedZalavaRuntime.class);
    when(runtime.modules()).thenReturn(List.of(module));
    when(runtime.state("notes"))
        .thenReturn(new ManagedZalavaRuntime.ModuleState(ManagedZalavaRuntime.State.STOPPED, ""));
    var store = new FileSystemModuleConfigurationStore(directory);
    ObjectProvider<BuildProperties> build = mock(ObjectProvider.class);
    var queries =
        new SelfKnowledgeConfiguration()
            .selfKnowledgeQueries(
                actors,
                accounts,
                runtime,
                mock(MemberToolAccess.class),
                mock(WebExtensionRoutes.class),
                mock(ModulePageAccess.class),
                mock(ModuleQueries.class),
                mock(ModuleSuggestions.class),
                mock(ChatProviderStatus.class),
                build,
                store,
                mock(ManagedServiceQueries.class));
    var actor = new Actor(id);

    assertThat(
            actors
                .call(actor, AccountRole.ADMIN, () -> queries.find("module:notes"))
                .orElseThrow()
                .configuration())
        .isEqualTo(ConfigurationState.SETUP_REQUIRED);
    var snapshot = new ModuleConfigurationSnapshot("notes", "1", "schema", Map.of(), Map.of());
    store.saveCandidate(snapshot, Map.of("token", "fixture-secret"));
    assertThat(
            actors
                .call(actor, AccountRole.ADMIN, () -> queries.find("module:notes"))
                .orElseThrow()
                .configuration())
        .isEqualTo(ConfigurationState.CHANGES_PENDING);
    store.promoteCandidate("notes");
    var active =
        actors.call(actor, AccountRole.ADMIN, () -> queries.find("module:notes")).orElseThrow();
    assertThat(active.configuration()).isEqualTo(ConfigurationState.ACTIVE);
    assertThat(active.toString()).doesNotContain("fixture-secret", "token");
    var reloadedStore = new FileSystemModuleConfigurationStore(directory);
    var reloaded =
        new SelfKnowledgeConfiguration()
            .selfKnowledgeQueries(
                actors,
                accounts,
                runtime,
                mock(MemberToolAccess.class),
                mock(WebExtensionRoutes.class),
                mock(ModulePageAccess.class),
                mock(ModuleQueries.class),
                mock(ModuleSuggestions.class),
                mock(ChatProviderStatus.class),
                build,
                reloadedStore,
                mock(ManagedServiceQueries.class));
    assertThat(
            actors
                .call(actor, AccountRole.ADMIN, () -> reloaded.find("module:notes"))
                .orElseThrow()
                .configuration())
        .isEqualTo(ConfigurationState.ACTIVE);
  }
}
