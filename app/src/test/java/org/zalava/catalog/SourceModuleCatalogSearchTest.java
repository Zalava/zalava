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
    assertThat(result.module().moduleId()).isEqualTo("sea-module-time");
    assertThat(result.module().artifact().groupId()).isEqualTo("org.zalava.modules");
    assertThat(result.module().artifact().artifactId()).isEqualTo("sea-module-time");
    assertThat(result.catalogEntry().sha256())
        .isEqualTo("aa34e2556c58a58b513a58ea9216e11f2c29c83ee7e74e422091006b51b23c7b");
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
    assertThat(result.module().moduleId()).isEqualTo("sea-module-shopping-list");
    assertThat(result.module().artifact().artifactId()).isEqualTo("sea-module-shopping-list");
    assertThat(result.catalogEntry().sha256())
        .isEqualTo("86779c237c123982aff458b1c7f419ebf433fec79e727ae692e6835dfa36ef75");
  }

  @Test
  void findsInstallableModuleByOperationName() {
    List<SourceModuleCatalogSearch.Result> results = search.search(catalog, "convert_time");

    assertThat(results)
        .extracting(result -> result.module().moduleId())
        .containsExactly("sea-module-time");
  }

  @Test
  void findsInstallableModuleByArtifactCoordinates() {
    List<SourceModuleCatalogSearch.Result> results = search.search(catalog, "org.zalava.modules");

    assertThat(results)
        .extracting(result -> result.module().artifact().artifactId())
        .containsExactly("sea-module-shopping-list", "sea-module-time");
  }

  @Test
  void emptyQueryReturnsAllCatalogEntries() {
    List<SourceModuleCatalogSearch.Result> results = search.search(catalog, "   ");

    assertThat(results)
        .extracting(result -> result.module().moduleId())
        .containsExactly("sea-module-shopping-list", "sea-module-time");
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
