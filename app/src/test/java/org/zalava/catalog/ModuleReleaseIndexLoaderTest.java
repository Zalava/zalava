package org.zalava.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModuleReleaseIndexLoaderTest {

  private final ModuleReleaseIndexLoader loader = new ModuleReleaseIndexLoader();

  @Test
  void loadsImmutableReleaseMetadata() {
    ModuleReleaseIndex index = loader.load(validIndex());

    assertThat(index.moduleId()).isEqualTo("sea-module-time");
    assertThat(index.releases())
        .singleElement()
        .satisfies(
            release -> {
              assertThat(release.version()).isEqualTo("1.0.1");
              assertThat(release.releaseTag()).isEqualTo("v1.0.1");
              assertThat(release.artifact().sha256()).isEqualTo("a".repeat(64));
              assertThat(release.security().permissions()).containsExactly("time.read");
            });
  }

  @Test
  void rejectsTagThatDoesNotIdentifyTheExactReleaseVersion() {
    assertThatThrownBy(
            () -> loader.load(validIndex().replace("releaseTag: v1.0.1", "releaseTag: v1.0.0")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("releases[0].releaseTag must match the immutable version tag");
  }

  @Test
  void rejectsArtifactDigestThatIsNotLowercaseSha256() {
    assertThatThrownBy(() -> loader.load(validIndex().replace("a".repeat(64), "A".repeat(64))))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("releases[0].artifact.sha256 must be a lowercase SHA-256 digest");
  }

  @Test
  void rejectsDuplicateReleaseVersions() {
    assertThatThrownBy(
            () -> loader.load(validIndex().replace("releases:\n", "releases:\n" + release())))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("releases[1].version must be unique");
  }

  private static String validIndex() {
    return """
                schemaVersion: 1
                moduleId: sea-module-time
                releases:
                %s
                """
        .formatted(release());
  }

  private static String release() {
    return """
                  - version: 1.0.1
                    releaseTag: v1.0.1
                    artifact:
                      groupId: org.zalava.modules
                      artifactId: sea-module-time
                      version: 1.0.1
                      sha256: %s
                    source:
                      repository: https://github.com/Zalava/zalava-module-time.git
                      license: Apache-2.0
                    compatibility:
                      seaRuntime: \">=1.0.0 <2.0.0\"
                    security:
                      permissions: [time.read]
                """
        .formatted("a".repeat(64));
  }
}
