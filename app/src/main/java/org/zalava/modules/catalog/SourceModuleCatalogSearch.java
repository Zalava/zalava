package org.zalava.modules.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SourceModuleCatalogSearch {

  public List<Result> search(SourceModuleCatalog catalog, String query) {
    String normalizedQuery = normalize(query);
    List<Result> results = new ArrayList<>();
    for (SourceModuleCatalog.Entry entry : catalog.entries()) {
      for (SourceModuleIndex.Module module : entry.index().modules()) {
        if (entry.moduleId().equals(module.moduleId()) && matches(module, normalizedQuery)) {
          results.add(new Result(module, entry, catalog.mavenRepositories()));
        }
      }
    }
    results.sort(
        Comparator.comparing((Result result) -> result.module().moduleId())
            .thenComparing(result -> result.module().version()));
    return results;
  }

  private static boolean matches(SourceModuleIndex.Module module, String normalizedQuery) {
    if (normalizedQuery.isBlank()) {
      return true;
    }
    return contains(module.moduleId(), normalizedQuery)
        || contains(module.displayName(), normalizedQuery)
        || contains(module.description(), normalizedQuery)
        || contains(module.artifact().groupId(), normalizedQuery)
        || contains(module.artifact().artifactId(), normalizedQuery)
        || module.operations().stream()
            .anyMatch(operation -> contains(operation.name(), normalizedQuery));
  }

  private static boolean contains(String value, String normalizedQuery) {
    return normalize(value).contains(normalizedQuery);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  public record Result(
      SourceModuleIndex.Module module,
      SourceModuleCatalog.Entry catalogEntry,
      List<SourceModuleCatalog.MavenRepository> repositories) {

    public Result {
      repositories = List.copyOf(repositories);
    }
  }
}
