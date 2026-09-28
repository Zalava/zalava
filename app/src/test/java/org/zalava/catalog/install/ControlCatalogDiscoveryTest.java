package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.catalog.install.application.ControlCatalogDiscovery;
import org.zalava.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

class ControlCatalogDiscoveryTest {

  @Test
  void exposesServerResolvedModulesAndPublishedReleaseVersions() {
    ModuleLocatorReleaseLocator locator =
        new ModuleLocatorReleaseLocator() {
          @Override
          public List<Module> modules() {
            return List.of(
                new Module("zalava-module-time", "Time", "Time tools"),
                new Module("zalava-module-tika", "Tika", "Content extraction"));
          }

          @Override
          public ResolvedModule resolve(String moduleId) {
            return new ResolvedModule(
                moduleId,
                moduleId,
                "description",
                URI.create("https://example.test/" + moduleId + "/releases/index.yaml"),
                URI.create("https://example.test/maven/" + moduleId));
          }
        };
    ModuleReleaseIndexRetrieval indexes =
        (uri, token) ->
            new ModuleReleaseIndex(
                1,
                "zalava-module-tika",
                List.of(release("1.0.1", "v1.0.1"), release("1.1.0", "v1.1.0")));

    ControlCatalogDiscovery discovery =
        new ControlCatalogDiscovery(locator, indexes, "secret-token");

    assertThat(discovery.modules())
        .extracting(ControlCatalogDiscovery.Module::moduleId)
        .containsExactly("zalava-module-time", "zalava-module-tika");
    assertThat(discovery.releases("zalava-module-tika"))
        .extracting(ControlCatalogDiscovery.Release::version)
        .containsExactly("1.1.0", "1.0.1");
  }

  private static ModuleReleaseIndex.Release release(String version, String tag) {
    return new ModuleReleaseIndex.Release(
        version,
        tag,
        new ModuleReleaseIndex.Artifact("org.example", "module", version, "a".repeat(64)),
        new ModuleReleaseIndex.Source(
            URI.create("https://github.com/example/module"), "Apache-2.0"),
        new ModuleReleaseIndex.Compatibility(">=1.0.0 <2.0.0"),
        new ModuleReleaseIndex.Security(List.of()));
  }
}
