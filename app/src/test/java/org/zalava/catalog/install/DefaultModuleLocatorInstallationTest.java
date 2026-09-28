package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.catalog.ModuleReleaseInstallRequest;
import org.zalava.catalog.install.application.DefaultModuleLocatorInstallation;
import org.zalava.catalog.install.application.port.in.ModuleLocatorInstallation;
import org.zalava.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

class DefaultModuleLocatorInstallationTest {

  @Test
  void delegatesOnlyCommitPinnedManifestAndFixedPackagesRepository() {
    CapturingInstallation releases = new CapturingInstallation();
    ModuleLocatorReleaseLocator locator =
        moduleId ->
            new ModuleLocatorReleaseLocator.ResolvedModule(
                moduleId,
                "Brave Search",
                "Search",
                URI.create(
                    "https://raw.githubusercontent.com/Zalava/zalava-module-brave-search/33590c1b3211fd6ba4021629460abfaf7c7e98ff/releases/index.yaml"),
                URI.create("https://maven.pkg.github.com/Zalava/zalava-module-brave-search"));

    new DefaultModuleLocatorInstallation(locator, releases)
        .create(new ModuleLocatorInstallation.Request("sea-module-brave-search", "1.0.2", null));

    assertThat(releases.request.manifestUri().getPath())
        .contains("33590c1b3211fd6ba4021629460abfaf7c7e98ff");
    assertThat(releases.request.bearerToken()).isNull();
    assertThat(releases.request.repositoryId()).isEqualTo("github-packages");
    assertThat(releases.request.repositoryUrl())
        .isEqualTo(URI.create("https://maven.pkg.github.com/Zalava/zalava-module-brave-search"));
  }

  private static final class CapturingInstallation implements ModuleReleaseInstallation {
    private Request request;

    @Override
    public ModuleReleaseInstallRequest create(Request request) {
      this.request = request;
      return null;
    }

    @Override
    public ModuleReleaseInstallRequest get(String requestId) {
      return null;
    }

    @Override
    public List<ModuleReleaseInstallRequest> recent(int limit) {
      return List.of();
    }

    @Override
    public ModuleReleaseInstallRequest allow(String requestId) {
      return null;
    }

    @Override
    public ModuleReleaseInstallRequest deny(String requestId) {
      return null;
    }
  }
}
