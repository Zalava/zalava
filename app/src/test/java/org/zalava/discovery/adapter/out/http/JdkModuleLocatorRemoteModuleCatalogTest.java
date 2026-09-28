package org.zalava.discovery.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.catalog.install.application.port.out.ModuleLocatorReleaseLocator;
import org.zalava.discovery.RemoteCatalogException;
import org.zalava.discovery.RemoteModuleCandidate;
import org.junit.jupiter.api.Test;

class JdkModuleLocatorRemoteModuleCatalogTest {

  private static final String DIGEST = "d".repeat(64);

  @Test
  void returnsCandidatesForModulesMatchingTheNormalizedQuery() {
    FakeLocator locator = new FakeLocator();
    locator.modules.add(module("sea-module-weather", "Weather", "Pollen forecast"));
    locator.modules.add(module("sea-module-time", "Time", "Clock and time"));
    locator.releases.put("sea-module-weather", index("sea-module-weather", "1.2.0", "pollen.read"));
    JdkModuleLocatorRemoteModuleCatalog catalog =
        new JdkModuleLocatorRemoteModuleCatalog(locator, locator, null, 5, 10);

    List<RemoteModuleCandidate> candidates = catalog.lookup("pollen");

    assertThat(catalog.configured()).isTrue();
    assertThat(candidates)
        .singleElement()
        .satisfies(
            candidate -> {
              assertThat(candidate.moduleId()).isEqualTo("sea-module-weather");
              assertThat(candidate.version()).isEqualTo("1.2.0");
              assertThat(candidate.digest()).isEqualTo(DIGEST);
              assertThat(candidate.permissions()).containsExactly("pollen.read");
            });
    assertThat(locator.resolved).containsExactly("sea-module-weather");
  }

  @Test
  void returnsNoCandidatesWhenNothingMatchesTheQuery() {
    FakeLocator locator = new FakeLocator();
    locator.modules.add(module("sea-module-time", "Time", "Clock and time"));
    JdkModuleLocatorRemoteModuleCatalog catalog =
        new JdkModuleLocatorRemoteModuleCatalog(locator, locator, null, 5, 10);

    assertThat(catalog.lookup("pollen")).isEmpty();
    assertThat(locator.resolved).isEmpty();
  }

  @Test
  void boundsTheNumberOfResolvedModules() {
    FakeLocator locator = new FakeLocator();
    locator.modules.add(module("sea-module-a", "Pollen A", "Pollen a"));
    locator.modules.add(module("sea-module-b", "Pollen B", "Pollen b"));
    locator.modules.add(module("sea-module-c", "Pollen C", "Pollen c"));
    locator.releases.put("sea-module-a", index("sea-module-a", "1.0.0", "read"));
    locator.releases.put("sea-module-b", index("sea-module-b", "1.0.0", "read"));
    locator.releases.put("sea-module-c", index("sea-module-c", "1.0.0", "read"));
    JdkModuleLocatorRemoteModuleCatalog catalog =
        new JdkModuleLocatorRemoteModuleCatalog(locator, locator, null, 2, 10);

    assertThat(catalog.lookup("pollen")).hasSize(2);
    assertThat(locator.resolved).containsExactly("sea-module-a", "sea-module-b");
  }

  @Test
  void boundsTheNumberOfCandidates() {
    FakeLocator locator = new FakeLocator();
    locator.modules.add(module("sea-module-weather", "Weather", "Pollen forecast"));
    ModuleReleaseIndex manyReleases =
        new ModuleReleaseIndex(
            1,
            "sea-module-weather",
            List.of(
                release("sea-module-weather", "1.0.0", "read"),
                release("sea-module-weather", "1.1.0", "read"),
                release("sea-module-weather", "1.2.0", "read")));
    locator.releases.put("sea-module-weather", manyReleases);
    JdkModuleLocatorRemoteModuleCatalog catalog =
        new JdkModuleLocatorRemoteModuleCatalog(locator, locator, null, 5, 2);

    assertThat(catalog.lookup("pollen")).hasSize(2);
  }

  @Test
  void mismatchedReleaseIndexIsRejected() {
    FakeLocator locator = new FakeLocator();
    locator.modules.add(module("sea-module-weather", "Weather", "Pollen forecast"));
    locator.releases.put("sea-module-weather", index("sea-module-other", "1.0.0", "read"));
    JdkModuleLocatorRemoteModuleCatalog catalog =
        new JdkModuleLocatorRemoteModuleCatalog(locator, locator, null, 5, 10);

    assertThatThrownBy(() -> catalog.lookup("pollen"))
        .isInstanceOf(RemoteCatalogException.class)
        .hasMessageContaining("does not match module id");
  }

  @Test
  void retrievalFailuresAreWrappedAsBoundedCatalogExceptions() {
    FakeLocator locator = new FakeLocator();
    locator.modules.add(module("sea-module-weather", "Weather", "Pollen forecast"));
    locator.fail = true;
    JdkModuleLocatorRemoteModuleCatalog catalog =
        new JdkModuleLocatorRemoteModuleCatalog(locator, locator, null, 5, 10);

    assertThatThrownBy(() -> catalog.lookup("pollen"))
        .isInstanceOf(RemoteCatalogException.class)
        .hasMessage("Remote module catalog lookup failed");
  }

  @Test
  void rejectsInvalidBounds() {
    FakeLocator locator = new FakeLocator();
    assertThatThrownBy(() -> new JdkModuleLocatorRemoteModuleCatalog(locator, locator, null, 0, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JdkModuleLocatorRemoteModuleCatalog(locator, locator, null, 5, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ModuleLocatorReleaseLocator.Module module(
      String moduleId, String displayName, String description) {
    return new ModuleLocatorReleaseLocator.Module(moduleId, displayName, description);
  }

  private static ModuleReleaseIndex index(String moduleId, String version, String permission) {
    return new ModuleReleaseIndex(1, moduleId, List.of(release(moduleId, version, permission)));
  }

  private static ModuleReleaseIndex.Release release(
      String moduleId, String version, String permission) {
    return new ModuleReleaseIndex.Release(
        version,
        "v" + version,
        new ModuleReleaseIndex.Artifact("org.zalava.modules", moduleId, version, DIGEST),
        new ModuleReleaseIndex.Source(
            URI.create("https://github.com/cordin/" + moduleId + ".git"), "Apache-2.0"),
        new ModuleReleaseIndex.Compatibility(">=1.0.0 <2.0.0"),
        new ModuleReleaseIndex.Security(List.of(permission)));
  }

  private static final class FakeLocator
      implements ModuleLocatorReleaseLocator, ModuleReleaseIndexRetrieval {
    private final List<ModuleLocatorReleaseLocator.Module> modules = new ArrayList<>();
    private final java.util.Map<String, ModuleReleaseIndex> releases = new java.util.HashMap<>();
    private final List<String> resolved = new ArrayList<>();
    private boolean fail;
    private String lastResolved;

    @Override
    public List<ModuleLocatorReleaseLocator.Module> modules() {
      return List.copyOf(modules);
    }

    @Override
    public ModuleLocatorReleaseLocator.ResolvedModule resolve(String moduleId) {
      resolved.add(moduleId);
      lastResolved = moduleId;
      return new ModuleLocatorReleaseLocator.ResolvedModule(
          moduleId,
          moduleId,
          moduleId,
          URI.create("https://example.test/" + moduleId + ".yaml"),
          URI.create("https://example.test/maven/" + moduleId));
    }

    @Override
    public ModuleReleaseIndex load(URI uri, String bearerToken) {
      if (fail) {
        throw new IllegalStateException("release index unavailable");
      }
      return releases.get(lastResolved);
    }
  }
}
