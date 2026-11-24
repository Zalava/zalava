package org.zalava.capabilities.selfknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.operation.adapter.out.runtime.ZalavaRuntimeProviderCatalog;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.RuntimeState;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;
import org.zalava.support.DocumentFixtureRuntime;

class SelfKnowledgeArtifactTest {
  @Test
  void discoversBuiltModuleArtifactWithoutExposingItsConfiguration() throws Exception {
    try (var runtime = new DocumentFixtureRuntime()) {
      var actors = new ActorExecutionContext();
      var accounts = mock(AccountLifecycle.class);
      var id = AccountId.newId();
      var account = mock(Account.class);
      when(accounts.findById(id)).thenReturn(Optional.of(account));
      when(account.enabled()).thenReturn(true);
      when(account.id()).thenReturn(id);
      when(account.role()).thenReturn(AccountRole.ADMIN);
      var queries =
          new DefaultSelfKnowledgeQueries(
              actors,
              accounts,
              runtime,
              new ZalavaRuntimeProviderCatalog(runtime),
              mock(MemberToolVisibility.class),
              mock(WebExtensionRoutes.class),
              mock(ModulePageAccess.class),
              ignored -> RuntimeState.UNKNOWN,
              mock(ModuleQueries.class),
              mock(ModuleSuggestions.class),
              () -> false,
              () -> "test-version");

      var result =
          actors.call(
              new Actor(id),
              AccountRole.ADMIN,
              () -> queries.find("module:zalava-external-module-fixture"));
      assertThat(result).isPresent();
      assertThat(result.orElseThrow().runtimeState()).isEqualTo(RuntimeState.ACTIVE);
      assertThat(result.toString()).doesNotContain("fixture-secret", "credentialRef");
    }
  }
}
