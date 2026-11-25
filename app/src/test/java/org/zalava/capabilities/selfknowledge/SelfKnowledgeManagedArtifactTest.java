package org.zalava.capabilities.selfknowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.assistant.chat.application.port.in.ChatProviderStatus;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.RuntimeState;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemModuleEnablement;
import org.zalava.modules.catalog.install.application.DefaultModuleQueries;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceQueries;
import org.zalava.modules.runtime.ExternalZalavaModuleLoader;
import org.zalava.modules.runtime.ManagedZalavaRuntime;
import org.zalava.modules.runtime.StaticZalavaModuleRegistry;
import org.zalava.modules.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

class SelfKnowledgeManagedArtifactTest {
  private static final String MODULE = "zalava-external-module-fixture";
  @TempDir Path workspace;

  @Test
  void builtArtifactStateFollowsStartStopAndRuntimeReconstruction() throws Exception {
    Path artifact =
        workspace.resolve(
            "source-module-installation/modules/" + MODULE + "/1.0.0/" + MODULE + "-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    Files.copy(Path.of(System.getProperty("zalava.test.external-module-jar")), artifact);
    String digest =
        "sha256:"
            + HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)));
    var registry = new FileSystemModuleEnablement(workspace);
    registry.enable(
        new ModuleEnablement.EnabledModule(
            MODULE, "1.0.0", artifact.toString(), digest, ">=0.1.0"));
    var lifecycle = new FileSystemModuleLifecycleStore(workspace);
    lifecycle.registerNewInstall(MODULE);
    var configurations = new FileSystemModuleConfigurationStore(workspace);
    var actors = new ActorExecutionContext();
    var accounts = mock(AccountLifecycle.class);
    var id = AccountId.newId();
    var account = mock(Account.class);
    when(accounts.findById(id)).thenReturn(Optional.of(account));
    when(account.enabled()).thenReturn(true);
    when(account.id()).thenReturn(id);
    when(account.role()).thenReturn(AccountRole.ADMIN);
    var actor = new Actor(id);

    try (var loader = new ExternalZalavaModuleLoader(registry)) {
      var modules = new StaticZalavaModuleRegistry(loader.loadModules());
      try (var runtime =
          new ManagedZalavaRuntime(
              modules,
              Set.of(),
              registry,
              lifecycle,
              configurations,
              ProviderFactoryContext.empty())) {
        var queries = queries(actors, accounts, runtime, registry, configurations);
        assertThat(find(queries, actors, actor).runtimeState()).isEqualTo(RuntimeState.STOPPED);
        runtime.start(MODULE);
        var active = find(queries, actors, actor);
        assertThat(active.runtimeState()).isEqualTo(RuntimeState.ACTIVE);
        assertThat(active.declaredRuntimeCompatibility()).isEqualTo(">=0.1.0");
        assertThat(active.services())
            .anyMatch(service -> service.id().equals(ContentExtractor.CONTRACT.serviceId()));
        runtime.stop(MODULE);
        assertThat(find(queries, actors, actor).runtimeState()).isEqualTo(RuntimeState.STOPPED);
        assertThat(find(queries, actors, actor).services()).isEmpty();
      }
      try (var restarted =
          new ManagedZalavaRuntime(
              modules,
              Set.of(),
              registry,
              lifecycle,
              configurations,
              ProviderFactoryContext.empty())) {
        var queries = queries(actors, accounts, restarted, registry, configurations);
        assertThat(find(queries, actors, actor).runtimeState()).isEqualTo(RuntimeState.STOPPED);
        restarted.start(MODULE);
        assertThat(find(queries, actors, actor).runtimeState()).isEqualTo(RuntimeState.ACTIVE);
      }
    }
  }

  private static SelfKnowledgeQueries queries(
      ActorExecutionContext actors,
      AccountLifecycle accounts,
      ManagedZalavaRuntime runtime,
      FileSystemModuleEnablement registry,
      FileSystemModuleConfigurationStore configurations) {
    ObjectProvider<BuildProperties> build = mock(ObjectProvider.class);
    return new SelfKnowledgeConfiguration()
        .selfKnowledgeQueries(
            actors,
            accounts,
            runtime,
            mock(MemberToolAccess.class),
            mock(WebExtensionRoutes.class),
            mock(ModulePageAccess.class),
            new DefaultModuleQueries(registry),
            mock(ModuleSuggestions.class),
            mock(ChatProviderStatus.class),
            build,
            configurations,
            mock(ManagedServiceQueries.class));
  }

  private static SelfKnowledgeQueries.Capability find(
      SelfKnowledgeQueries queries, ActorExecutionContext actors, Actor actor) {
    return actors
        .call(actor, AccountRole.ADMIN, () -> queries.find("module:" + MODULE))
        .orElseThrow();
  }
}
