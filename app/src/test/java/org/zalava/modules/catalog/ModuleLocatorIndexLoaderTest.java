package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModuleLocatorIndexLoaderTest {

  private final ModuleLocatorIndexLoader loader = new ModuleLocatorIndexLoader();

  @Test
  void loadsDedicatedLocatorWithoutReleaseEvidence() {
    ModuleLocatorIndex index = loader.load(catalog());

    assertThat(index.modules())
        .singleElement()
        .satisfies(
            module -> {
              assertThat(module.moduleId()).isEqualTo("zalava-module-brave-search");
              assertThat(module.repository())
                  .hasToString("https://github.com/Zalava/zalava-module-brave-search");
              assertThat(module.releaseIndexPath()).isEqualTo("releases/index.yaml");
            });
  }

  @Test
  void rejectsLocatorThatDuplicatesArtifactOrReleaseEvidence() {
    assertThatThrownBy(
            () ->
                loader.load(
                    catalog()
                        .replace(
                            "releaseIndexPath: releases/index.yaml",
                            "releaseIndexPath: releases/index.yaml\n    version: 1.0.2")))
        .hasMessageContaining("modules[0] contains unsupported field: version");
  }

  @Test
  void rejectsNonGithubModuleRepositories() {
    assertThatThrownBy(
            () ->
                loader.load(
                    catalog()
                        .replace(
                            "https://github.com/Zalava/zalava-module-brave-search",
                            "https://example.test/module")))
        .hasMessageContaining("modules[0].repository must be an HTTPS GitHub repository URI");
  }

  private static String catalog() {
    return """
                schemaVersion: 1
                repository:
                  type: module-locator
                  indexRepository: https://github.com/Zalava/zalava-catalog
                  indexPath: catalog.yaml
                modules:
                  - moduleId: zalava-module-brave-search
                    displayName: Brave Search
                    description: Provider-scoped web search backed by Brave Search
                    repository: https://github.com/Zalava/zalava-module-brave-search
                    releaseIndexPath: releases/index.yaml
                """;
  }
}
