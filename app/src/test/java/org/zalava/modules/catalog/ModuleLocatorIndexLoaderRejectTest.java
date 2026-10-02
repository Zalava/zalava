package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Additional behavior tests for {@link ModuleLocatorIndexLoader} covering the structural rejections
 * not exercised by {@link ModuleLocatorIndexLoaderTest} (which covers the happy path, duplicate
 * evidence, and non-GitHub repos).
 */
class ModuleLocatorIndexLoaderRejectTest {

  private final ModuleLocatorIndexLoader loader = new ModuleLocatorIndexLoader();

  @Test
  void rejectsInvalidYaml() {
    assertThatThrownBy(() -> loader.load("schemaVersion: ["))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("catalog must be valid YAML");
  }

  @Test
  void rejectsNonObjectRoot() {
    assertThatThrownBy(() -> loader.load("- just\n- a\n- list\n"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("catalog must be an object");
  }

  @Test
  void rejectsUnsupportedFieldAtRoot() {
    assertThatThrownBy(
            () -> loader.load(validCatalog().replace("modules:\n", "extra: 1\nmodules:\n")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("catalog contains unsupported field: extra");
  }

  @Test
  void rejectsWrongSchemaVersionType() {
    assertThatThrownBy(
            () -> loader.load(validCatalog().replace("schemaVersion: 1", "schemaVersion: one")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("schemaVersion must be an integer");
  }

  @Test
  void rejectsWrongSchemaVersionValue() {
    assertThatThrownBy(
            () -> loader.load(validCatalog().replace("schemaVersion: 1", "schemaVersion: 2")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("schemaVersion must be 1");
  }

  @Test
  void rejectsWrongRepositoryType() {
    assertThatThrownBy(
            () -> loader.load(validCatalog().replace("type: module-locator", "type: other")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("repository.type must be module-locator");
  }

  @Test
  void rejectsUnsupportedRepositoryField() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validCatalog()
                        .replace("indexPath: catalog.yaml", "indexPath: catalog.yaml\n  extra: 1")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("repository contains unsupported field: extra");
  }

  @Test
  void rejectsNonHttpsIndexRepository() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validCatalog()
                        .replace(
                            "https://github.com/Zalava/zalava-catalog",
                            "http://github.com/Zalava/zalava-catalog")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("repository.indexRepository");
  }

  @Test
  void rejectsQueryInIndexRepository() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validCatalog()
                        .replace(
                            "https://github.com/Zalava/zalava-catalog",
                            "https://github.com/Zalava/zalava-catalog?tab=readme")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("repository.indexRepository");
  }

  @Test
  void rejectsNonYamlIndexPath() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validCatalog().replace("indexPath: catalog.yaml", "indexPath: catalog.json")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("repository.indexPath must name a YAML file");
  }

  @Test
  void rejectsDotDotIndexPath() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validCatalog()
                        .replace("indexPath: catalog.yaml", "indexPath: ../catalog.yaml")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("repository.indexPath must be a relative path without dot segments");
  }

  @Test
  void rejectsEmptyModulesList() {
    assertThatThrownBy(
            () ->
                loader.load(
                    "schemaVersion: 1\nrepository:\n  type: module-locator\n  indexRepository: https://github.com/Zalava/zalava-catalog\n  indexPath: catalog.yaml\nmodules: []\n"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules must be a non-empty list");
  }

  @Test
  void rejectsUnsupportedModuleField() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validCatalog()
                        .replace(
                            "releaseIndexPath: releases/index.yaml",
                            "releaseIndexPath: releases/index.yaml\n    extra: 1")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0] contains unsupported field: extra");
  }

  @Test
  void rejectsDuplicateModuleIds() {
    String dup = validCatalog().replace("modules:\n", "modules:\n" + moduleBlock());

    assertThatThrownBy(() -> loader.load(dup))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[1].moduleId must be unique");
  }

  @Test
  void rejectsNonYamlModuleReleaseIndexPath() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validCatalog()
                        .replace(
                            "releaseIndexPath: releases/index.yaml",
                            "releaseIndexPath: releases/index.txt")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0].releaseIndexPath must name a YAML file");
  }

  @Test
  void rejectsBlankModuleDisplayName() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validCatalog().replace("displayName: Brave Search", "displayName: \"\"")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0].displayName must be a non-blank string");
  }

  private static String validCatalog() {
    return "schemaVersion: 1\n"
        + "repository:\n"
        + "  type: module-locator\n"
        + "  indexRepository: https://github.com/Zalava/zalava-catalog\n"
        + "  indexPath: catalog.yaml\n"
        + "modules:\n"
        + moduleBlock();
  }

  private static String moduleBlock() {
    return "  - moduleId: zalava-module-brave-search\n"
        + "    displayName: Brave Search\n"
        + "    description: Provider-scoped web search backed by Brave Search\n"
        + "    repository: https://github.com/Zalava/zalava-module-brave-search\n"
        + "    releaseIndexPath: releases/index.yaml\n";
  }
}
