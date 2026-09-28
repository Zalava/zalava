package org.zalava;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.zalava.catalog.FileSystemModuleConfigurationStore;
import org.zalava.catalog.ModuleConfigurationSnapshot;
import org.zalava.runtime.SeaModuleProperties;

class SeaConfigurationTest {

  @TempDir Path root;

  @Test
  void leavesPersistedCandidateInactiveUntilTheModuleLifecycleAppliesIt() {
    FileSystemModuleConfigurationStore store = new FileSystemModuleConfigurationStore(root);
    store.saveCandidate(
        new ModuleConfigurationSnapshot(
            "sea-module-brave-search",
            "1.0.0",
            "schema-1",
            Map.of("brave-search", Map.of("apiKeyRef", "brave-key")),
            Map.of("brave-search.apiKeyRef", "brave-key")),
        Map.of("brave-key", "test-secret"));

    ProviderFactoryContext context =
        new SeaConfiguration()
            .seaProviderFactoryContext(
                new SeaModuleProperties(Map.of("other-module", Map.of("factories", Map.of()))),
                store);

    assertThat(store.candidate("sea-module-brave-search")).isPresent();
    assertThat(store.active("sea-module-brave-search")).isEmpty();
    ProviderFactoryContext factory = context.forFactory("sea-module-brave-search", "brave-search");
    assertThat(factory.configuration()).isEmpty();
    assertThat(factory.secrets().resolve("brave-key")).isEmpty();
    assertThat(context.forFactory("other-module", "missing").secrets().resolve("brave-key"))
        .isEmpty();
  }

  @Test
  void resolvesModuleConfigurationBesideManagedPrivateConfigurationImport() {
    FileSystemModuleConfigurationStore store =
        new SeaConfiguration()
            .moduleConfigurationStore(
                new MockEnvironment()
                    .withProperty(
                        "spring.config.import",
                        "optional:classpath:application.private.yaml,optional:file:"
                            + root
                            + "/application.private.yaml"));
    ModuleConfigurationSnapshot snapshot =
        new ModuleConfigurationSnapshot("test-module", "1.0.0", "schema-1", Map.of(), Map.of());

    store.saveCandidate(snapshot, Map.of());

    assertThat(store.candidate("test-module")).contains(snapshot);
    assertThat(root.resolve("module-configuration/test-module/candidate/snapshot.json")).exists();
  }

  @Test
  void storesModuleConfigurationBesideWritableFileWorkspaceBeforeReadOnlyPrivateImport() {
    Path workspace = root.resolve("workspace");
    FileSystemModuleConfigurationStore store =
        new SeaConfiguration()
            .moduleConfigurationStore(
                new MockEnvironment()
                    .withProperty("agent.workspace", workspace.toUri().toString())
                    .withProperty(
                        "spring.config.import",
                        "optional:file:" + root + "/private/application.private.yaml"));
    ModuleConfigurationSnapshot snapshot =
        new ModuleConfigurationSnapshot("test-module", "1.0.0", "schema-1", Map.of(), Map.of());

    store.saveCandidate(snapshot, Map.of());

    assertThat(root.resolve("module-configuration/test-module/candidate/snapshot.json")).exists();
    assertThat(workspace.resolve("module-configuration")).doesNotExist();
  }

  @Test
  void fallsBackToPrivateImportForNonHierarchicalWorkspaceUri() {
    FileSystemModuleConfigurationStore store =
        new SeaConfiguration()
            .moduleConfigurationStore(
                new MockEnvironment()
                    .withProperty("agent.workspace", "file:workspace")
                    .withProperty(
                        "spring.config.import",
                        "optional:file:" + root + "/private/application.private.yaml"));
    ModuleConfigurationSnapshot snapshot =
        new ModuleConfigurationSnapshot("test-module", "1.0.0", "schema-1", Map.of(), Map.of());

    store.saveCandidate(snapshot, Map.of());

    assertThat(root.resolve("private/module-configuration/test-module/candidate/snapshot.json"))
        .exists();
  }

  @Test
  void combinesOnlyConfiguredBuiltInsAndExternalModules() {
    SeaModule builtIn = module("test-built-in");
    SeaModule external = module("sea-module-filesystem");

    assertThat(SeaConfiguration.mergeSeaModules(Set.of(builtIn), List.of(external)))
        .containsExactlyInAnyOrder(builtIn, external);
  }

  private static SeaModule module(String moduleId) {
    return new SeaModule() {
      @Override
      public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(moduleId, "1.0.0", moduleId, "Test module");
      }

      @Override
      public List<org.zalava.ProviderFactory> providerFactories() {
        return List.of();
      }
    };
  }
}
