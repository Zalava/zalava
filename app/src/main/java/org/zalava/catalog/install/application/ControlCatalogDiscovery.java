package org.zalava.catalog.install.application;

import java.util.Comparator;
import java.util.List;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

/** Server-owned, bounded catalog data for the administrator control surface. */
public final class ControlCatalogDiscovery {
  private final ModuleLocatorReleaseLocator locator;
  private final ModuleReleaseIndexRetrieval releaseIndexes;
  private final String githubToken;

  public ControlCatalogDiscovery(
      ModuleLocatorReleaseLocator locator,
      ModuleReleaseIndexRetrieval releaseIndexes,
      String githubToken) {
    this.locator = locator;
    this.releaseIndexes = releaseIndexes;
    this.githubToken = githubToken;
  }

  public List<Module> modules() {
    return locator.modules().stream()
        .map(module -> new Module(module.moduleId(), module.displayName(), module.description()))
        .toList();
  }

  public List<Release> releases(String moduleId) {
    ModuleLocatorReleaseLocator.ResolvedModule module = locator.resolve(moduleId);
    ModuleReleaseIndex index = releaseIndexes.load(module.manifestUri(), githubToken);
    if (!moduleId.equals(index.moduleId())) {
      throw new SourceModuleInstallationException(
          "Module release index does not match catalog module: " + moduleId);
    }
    return index.releases().stream()
        .map(release -> new Release(release.version(), release.releaseTag()))
        .sorted(Comparator.comparing(Release::version).reversed())
        .toList();
  }

  public record Module(String moduleId, String displayName, String description) {}

  public record Release(String version, String releaseTag) {}
}
