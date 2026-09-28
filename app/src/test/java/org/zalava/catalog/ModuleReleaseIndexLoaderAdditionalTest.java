package org.zalava.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModuleReleaseIndexLoaderAdditionalTest {

  private final ModuleReleaseIndexLoader loader = new ModuleReleaseIndexLoader();

  @Test
  void rejectsInvalidSchemaVersion() {
    assertThatThrownBy(() -> loader.load("schemaVersion: 2\nmoduleId: m\nreleases: []"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("schemaVersion must be 1");
  }

  @Test
  void rejectsMissingModuleId() {
    assertThatThrownBy(() -> loader.load("schemaVersion: 1\nreleases: []"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("moduleId must be a non-blank string");
  }

  @Test
  void rejectsEmptyReleases() {
    assertThatThrownBy(() -> loader.load("schemaVersion: 1\nmoduleId: m\nreleases: []"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("releases must not be empty");
  }

  @Test
  void rejectsMissingReleases() {
    assertThatThrownBy(() -> loader.load("schemaVersion: 1\nmoduleId: m"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("releases must be a list");
  }

  @Test
  void rejectsNonObjectReleaseEntry() {
    assertThatThrownBy(
            () -> loader.load("schemaVersion: 1\nmoduleId: m\nreleases:\n  - not-a-map\n"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("releases[0] must be an object");
  }

  @Test
  void rejectsInvalidVersionFormat() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "not-semver"
                        releaseTag: "vnot-semver"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "not-semver"
                          sha256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        source:
                          repository: "https://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("version must be a semantic version");
  }

  @Test
  void rejectsMismatchedReleaseTag() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "1.0.0"
                        releaseTag: "v2.0.0"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "1.0.0"
                          sha256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        source:
                          repository: "https://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must match the immutable version tag");
  }

  @Test
  void rejectsMismatchedArtifactVersion() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "1.0.0"
                        releaseTag: "v1.0.0"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "2.0.0"
                          sha256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        source:
                          repository: "https://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must match release version");
  }

  @Test
  void rejectsDuplicateVersions() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "1.0.0"
                        releaseTag: "v1.0.0"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "1.0.0"
                          sha256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        source:
                          repository: "https://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                      - version: "1.0.0"
                        releaseTag: "v1.0.0"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "1.0.0"
                          sha256: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                        source:
                          repository: "https://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("releases[1].version must be unique");
  }

  @Test
  void rejectsInvalidSha256() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "1.0.0"
                        releaseTag: "v1.0.0"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "1.0.0"
                          sha256: "not-hex"
                        source:
                          repository: "https://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must be a lowercase SHA-256 digest");
  }

  @Test
  void rejectsNonHttpsRepository() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "1.0.0"
                        releaseTag: "v1.0.0"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "1.0.0"
                          sha256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        source:
                          repository: "http://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must be a valid HTTPS URI");
  }

  @Test
  void rejectsMissingArtifact() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "1.0.0"
                        releaseTag: "v1.0.0"
                        source:
                          repository: "https://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("artifact must be an object");
  }

  @Test
  void rejectsBlankLicense() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "1.0.0"
                        releaseTag: "v1.0.0"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "1.0.0"
                          sha256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        source:
                          repository: "https://github.com/x"
                          license: "  "
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: []
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("license must be a non-blank string");
  }

  @Test
  void rejectsInvalidYaml() {
    assertThatThrownBy(() -> loader.load("{{bad yaml"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must be valid YAML");
  }

  @Test
  void rejectsNonIntegerSchemaVersion() {
    assertThatThrownBy(() -> loader.load("schemaVersion: \"one\"\nmoduleId: m\nreleases: []"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("schemaVersion must be an integer");
  }

  @Test
  void rejectsNonObjectRoot() {
    assertThatThrownBy(() -> loader.load("just a string"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must be an object");
  }

  @Test
  void rejectsNonListPermissions() {
    assertThatThrownBy(
            () ->
                loader.load(
                    """
                    schemaVersion: 1
                    moduleId: m
                    releases:
                      - version: "1.0.0"
                        releaseTag: "v1.0.0"
                        artifact:
                          groupId: g
                          artifactId: a
                          version: "1.0.0"
                          sha256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        source:
                          repository: "https://github.com/x"
                          license: MIT
                        compatibility:
                          seaRuntime: ">=1.0.0"
                        security:
                          permissions: "not-a-list"
                    """))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("permissions must be a list");
  }
}
