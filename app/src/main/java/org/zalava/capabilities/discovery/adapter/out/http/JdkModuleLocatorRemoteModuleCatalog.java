package org.zalava.capabilities.discovery.adapter.out.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.zalava.capabilities.discovery.RemoteCatalogException;
import org.zalava.capabilities.discovery.RemoteModuleCandidate;
import org.zalava.capabilities.discovery.application.port.out.RemoteModuleCatalog;
import org.zalava.modules.catalog.ModuleReleaseIndex;
import org.zalava.modules.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

/**
 * Bounded remote catalog over the configured module locator and its immutable, commit-pinned
 * release indexes. It only reads metadata; it never downloads or installs an artifact.
 */
public final class JdkModuleLocatorRemoteModuleCatalog implements RemoteModuleCatalog {

  private final ModuleLocatorReleaseLocator locator;
  private final ModuleReleaseIndexRetrieval releaseIndexes;
  private final String accessToken;
  private final int maxModules;
  private final int maxCandidates;

  public JdkModuleLocatorRemoteModuleCatalog(
      ModuleLocatorReleaseLocator locator,
      ModuleReleaseIndexRetrieval releaseIndexes,
      String accessToken,
      int maxModules,
      int maxCandidates) {
    if (maxModules < 1) {
      throw new IllegalArgumentException("Remote module discovery maxModules must be positive");
    }
    if (maxCandidates < 1) {
      throw new IllegalArgumentException("Remote module discovery maxCandidates must be positive");
    }
    this.locator = locator;
    this.releaseIndexes = releaseIndexes;
    this.accessToken = accessToken == null || accessToken.isBlank() ? null : accessToken;
    this.maxModules = maxModules;
    this.maxCandidates = maxCandidates;
  }

  @Override
  public boolean configured() {
    return true;
  }

  @Override
  public List<RemoteModuleCandidate> lookup(String normalizedQuery) {
    try {
      List<ModuleLocatorReleaseLocator.Module> matched =
          locator.modules().stream()
              .filter(module -> matches(module, normalizedQuery))
              .sorted(Comparator.comparing(ModuleLocatorReleaseLocator.Module::moduleId))
              .limit(maxModules)
              .toList();
      List<RemoteModuleCandidate> candidates = new ArrayList<>();
      for (ModuleLocatorReleaseLocator.Module module : matched) {
        ModuleLocatorReleaseLocator.ResolvedModule resolved = locator.resolve(module.moduleId());
        ModuleReleaseIndex index = releaseIndexes.load(resolved.manifestUri(), accessToken);
        if (!module.moduleId().equals(index.moduleId())) {
          throw new RemoteCatalogException(
              "Remote module release index does not match module id: " + module.moduleId());
        }
        for (ModuleReleaseIndex.Release release : index.releases()) {
          candidates.add(
              new RemoteModuleCandidate(
                  index.moduleId(),
                  release.version(),
                  release.artifact().sha256(),
                  module.displayName(),
                  module.description(),
                  release.security().permissions()));
          if (candidates.size() >= maxCandidates) {
            return List.copyOf(candidates);
          }
        }
      }
      return List.copyOf(candidates);
    } catch (RemoteCatalogException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new RemoteCatalogException("Remote module catalog lookup failed", exception);
    }
  }

  private static boolean matches(ModuleLocatorReleaseLocator.Module module, String query) {
    String moduleId = normalize(module.moduleId());
    String displayName = normalize(module.displayName());
    String description = normalize(module.description());
    return Arrays.stream(query.split(" "))
        .filter(term -> !term.isBlank())
        .anyMatch(
            term ->
                containsPhrase(moduleId, term)
                    || containsPhrase(displayName, term)
                    || containsPhrase(description, term));
  }

  private static boolean containsPhrase(String candidate, String phrase) {
    return (" " + candidate + " ").contains(" " + phrase + " ");
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim()
        .replaceAll(" +", " ");
  }
}
