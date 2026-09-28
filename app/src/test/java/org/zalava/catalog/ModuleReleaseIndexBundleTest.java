package org.zalava.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModuleReleaseIndexBundleTest {
  private final ModuleReleaseIndexLoader loader = new ModuleReleaseIndexLoader();

  @Test
  void readsAnOrderedRuntimeArtifactBundleFromSchemaOneIndex() {
    ModuleReleaseIndex index =
        loader.load(
            index(
                """
        runtimeArtifacts:
          - groupId: org.example
            artifactId: dependency-one
            version: "2.0.0"
            sha256: "%s"
          - groupId: org.example
            artifactId: dependency-two
            version: "3.0.0"
            sha256: "%s"
        """
                    .formatted("b".repeat(64), "c".repeat(64))));

    assertThat(index.releases().getFirst().runtimeArtifacts())
        .extracting(ModuleReleaseIndex.Artifact::artifactId)
        .containsExactly("dependency-one", "dependency-two");
  }

  @Test
  void keepsAClassicSingleArtifactReleaseAsAnEmptyBundleExtension() {
    assertThat(loader.load(index("")).releases().getFirst().runtimeArtifacts()).isEmpty();
  }

  @Test
  void readsAnExplicitSingleDownloadBundleMarker() {
    assertThat(loader.load(index("artifactBundle: true\n")).releases().getFirst().artifactBundle())
        .isTrue();
    assertThatThrownBy(() -> loader.load(index("artifactBundle: \"true\"\n")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must be a boolean");
  }

  @Test
  void rejectsRuntimeArtifactThatDuplicatesPrimaryIdentity() {
    assertThatThrownBy(
            () ->
                loader.load(
                    index(
                        """
                        runtimeArtifacts:
                          - groupId: org.example
                            artifactId: sea-module-example
                            version: "1.0.0"
                            sha256: "%s"
                        """
                            .formatted("b".repeat(64)))))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must not duplicate");
  }

  @Test
  void rejectsDuplicateRuntimeArtifactIdentityAndUnsafeCoordinates() {
    assertThatThrownBy(
            () ->
                loader.load(
                    index(
                        """
                        runtimeArtifacts:
                          - groupId: org.example
                            artifactId: dependency
                            version: "2.0.0"
                            sha256: "%s"
                          - groupId: org.example
                            artifactId: dependency
                            version: "2.0.0"
                            sha256: "%s"
                        """
                            .formatted("b".repeat(64), "c".repeat(64)))))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("must not duplicate");
    assertThatThrownBy(
            () ->
                loader.load(
                    index(
                        """
                        runtimeArtifacts:
                          - groupId: ../escape
                            artifactId: dependency
                            version: "2.0.0"
                            sha256: "%s"
                        """
                            .formatted("b".repeat(64)))))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("safe Maven coordinate");
  }

  private static String index(String runtimeArtifacts) {
    return """
        schemaVersion: 1
        moduleId: sea-module-example
        releases:
          - version: "1.0.0"
            releaseTag: "v1.0.0"
            artifact:
              groupId: org.example
              artifactId: sea-module-example
              version: "1.0.0"
              sha256: "%s"
            %s
            source:
              repository: https://github.com/example/sea-module-example
              license: MIT
            compatibility:
              seaRuntime: ">=1.0.0"
            security:
              permissions: []
        """
        .formatted("a".repeat(64), runtimeArtifacts.indent(4).replaceFirst("^ {4}", ""));
  }
}
