package org.zalava.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceModuleCatalogSearchTest {

  private final SourceModuleCatalog catalog = loadCatalog();
  private final SourceModuleCatalogSearch search = new SourceModuleCatalogSearch();

  @Test
  void findsInstallableModuleByModuleName() {
    List<SourceModuleCatalogSearch.Result> results = search.search(catalog, "time");

    assertThat(results).hasSize(1);
    SourceModuleCatalogSearch.Result result = results.getFirst();
    assertThat(result.module().moduleId()).isEqualTo("zalava-module-time");
    assertThat(result.module().artifact().groupId()).isEqualTo("org.zalava.modules");
    assertThat(result.module().artifact().artifactId()).isEqualTo("zalava-module-time");
    assertThat(result.catalogEntry().sha256())
        .isEqualTo("c1b6523987aa570a03ca1c8be8391fcab72b6fcdabde308eaa7da97ed3587147");
    assertThat(result.repositories())
        .containsExactly(
            new SourceModuleCatalog.MavenRepository(
                "maven-central", "https://repo.maven.apache.org/maven2"));
  }

  @Test
  void findsShoppingListModuleByModuleName() {
    List<SourceModuleCatalogSearch.Result> results = search.search(catalog, "shopping");

    assertThat(results).hasSize(1);
    SourceModuleCatalogSearch.Result result = results.getFirst();
    assertThat(result.module().moduleId()).isEqualTo("zalava-module-shopping-list");
    assertThat(result.module().artifact().artifactId()).isEqualTo("zalava-module-shopping-list");
    assertThat(result.catalogEntry().sha256())
        .isEqualTo("12a285d3fd7ea2e446ba5398d1bbf98597c74814347217eeb92aff5910a06210");
  }

  @Test
  void findsInstallableModuleByOperationName() {
    List<SourceModuleCatalogSearch.Result> results = search.search(catalog, "convert_time");

    assertThat(results)
        .extracting(result -> result.module().moduleId())
        .containsExactly("zalava-module-time");
  }

  @Test
  void findsInstallableModuleByArtifactCoordinates() {
    List<SourceModuleCatalogSearch.Result> results = search.search(catalog, "org.zalava.modules");

    assertThat(results)
        .extracting(result -> result.module().artifact().artifactId())
        .containsExactly("zalava-module-shopping-list", "zalava-module-time");
  }

  @Test
  void emptyQueryReturnsAllCatalogEntries() {
    List<SourceModuleCatalogSearch.Result> results = search.search(catalog, "   ");

    assertThat(results)
        .extracting(result -> result.module().moduleId())
        .containsExactly("zalava-module-shopping-list", "zalava-module-time");
  }

  @Test
  void returnsNoResultsForUnknownQuery() {
    assertThat(search.search(catalog, "calendar")).isEmpty();
  }

  private static SourceModuleCatalog loadCatalog() {
    try {
      return new SourceModuleCatalogLoader()
          .load(Path.of("..", "docs", "source-modules", "catalog.yaml"));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to load checked-in source module catalog", exception);
    }
  }
}
