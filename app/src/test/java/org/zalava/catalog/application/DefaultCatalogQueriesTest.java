package org.zalava.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.SourceModuleCatalog;
import org.zalava.catalog.SourceModuleIndex;

class DefaultCatalogQueriesTest {
  private final DefaultCatalogQueries queries = new DefaultCatalogQueries();

  @Test
  void searchesAndSelectsThroughTheInboundCatalogPort() {
    SourceModuleIndex.Module module = module("zalava-module-time", "1.0.1");
    SourceModuleCatalog catalog =
        new SourceModuleCatalog(
            1,
            new SourceModuleCatalog.Repository(
                "source", "https://example.test/index", "index.yaml"),
            List.of(),
            List.of(
                new SourceModuleCatalog.Entry(
                    "zalava-module-time",
                    "time.yaml",
                    "a".repeat(64),
                    new SourceModuleIndex(1, List.of(module)))));

    assertThat(queries.search(catalog, "time"))
        .extracting(result -> result.module().moduleId())
        .containsExactly("zalava-module-time");
    assertThat(queries.selectRelease(releaseIndex(), "zalava-module-time", "1.0.1").module())
        .extracting(SourceModuleIndex.Module::moduleId, SourceModuleIndex.Module::version)
        .containsExactly("zalava-module-time", "1.0.1");
  }

  private static ModuleReleaseIndex releaseIndex() {
    return new ModuleReleaseIndex(
        1,
        "zalava-module-time",
        List.of(
            new ModuleReleaseIndex.Release(
                "1.0.1",
                "v1.0.1",
                new ModuleReleaseIndex.Artifact("org.example", "time", "1.0.1", "a".repeat(64)),
                new ModuleReleaseIndex.Source(
                    URI.create("https://example.test/time"), "Apache-2.0"),
                new ModuleReleaseIndex.Compatibility(">=1.0.0"),
                new ModuleReleaseIndex.Security(List.of()))));
  }

  private static SourceModuleIndex.Module module(String id, String version) {
    return new SourceModuleIndex.Module(
        id,
        version,
        "Time",
        "Time",
        URI.create("https://example.test/time"),
        new SourceModuleIndex.Artifact("org.example", "time", version),
        new SourceModuleIndex.Source(URI.create("https://example.test/time"), "Apache-2.0"),
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
  }
}
