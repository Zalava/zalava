package org.zalava.ui;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.zalava.catalog.ModuleReleaseVersion;
import org.zalava.catalog.install.application.ControlCatalogDiscovery;

/**
 * Bounded, refreshed view of the module catalog for the product Modules screen. A page view never
 * performs network I/O on its own: the catalog module list is only replaced by an explicit refresh,
 * and per-module releases are loaded once and cached until the next refresh.
 */
@Component
public class ModuleMarketplace {

  private static final int MAX_MODULES = 100;
  private static final int MAX_RELEASES = 50;

  private final ControlCatalogDiscovery discovery;
  private final Map<String, List<Release>> releasesByModule = new ConcurrentHashMap<>();
  private volatile Snapshot snapshot = Snapshot.empty();

  public ModuleMarketplace(ControlCatalogDiscovery discovery) {
    this.discovery = discovery;
  }

  public synchronized Snapshot refresh() {
    releasesByModule.clear();
    List<Module> modules =
        discovery.modules().stream()
            .map(
                module -> new Module(module.moduleId(), module.displayName(), module.description()))
            .sorted(Comparator.comparing(Module::moduleId))
            .limit(MAX_MODULES)
            .toList();
    snapshot = new Snapshot(modules, Instant.now().toString());
    return snapshot;
  }

  public Snapshot snapshot() {
    return snapshot;
  }

  public List<Release> releases(String moduleId) {
    requireCatalogModule(moduleId);
    return releasesByModule.computeIfAbsent(moduleId, this::loadReleases);
  }

  public Release requireAvailable(String moduleId, String version) {
    return releases(moduleId).stream()
        .filter(release -> release.version().equals(version))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Release version is not available in the refreshed catalog: " + version));
  }

  private List<Release> loadReleases(String moduleId) {
    return discovery.releases(moduleId).stream()
        .map(release -> new Release(release.version(), release.releaseTag()))
        .sorted((left, right) -> ModuleReleaseVersion.compare(right.version(), left.version()))
        .limit(MAX_RELEASES)
        .toList();
  }

  private void requireCatalogModule(String moduleId) {
    if (snapshot.modules().stream().noneMatch(module -> module.moduleId().equals(moduleId))) {
      throw new IllegalArgumentException(
          "Catalog module is not available; check the catalog and choose a module");
    }
  }

  public record Snapshot(List<Module> modules, String refreshedAt) {
    static Snapshot empty() {
      return new Snapshot(List.of(), null);
    }
  }

  public record Module(String moduleId, String displayName, String description) {}

  public record Release(String version, String releaseTag) {}
}
