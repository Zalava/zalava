package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInspection;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalModuleProjectReleaseLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalModuleProjectReleaseLocatorTest {
  @TempDir Path root;

  @Test
  void resolvesTheReleaseIndexedJarUnderBuildLibsWithoutRunningABuild() throws Exception {
    Path project = root.resolve("module");
    Path jar = project.resolve("build/libs/sea-module-example-1.2.3.jar");
    Files.createDirectories(jar.getParent());
    Files.writeString(jar, "built module binary");
    Files.createDirectories(project.resolve("releases"));
    Files.writeString(project.resolve("releases/index.yaml"), index(digest(jar)));

    var locator =
        new FileSystemLocalModuleProjectReleaseLocator(
            new FileSystemLocalArtifactInspection(java.util.List.of(root)));
    var resolved = locator.resolve(project.toString(), "sea-module-example", "1.2.3");

    assertThat(resolved.release().module().moduleId()).isEqualTo("sea-module-example");
    assertThat(resolved.artifact().path()).isEqualTo(jar.toRealPath().toString());
  }

  @Test
  void rejectsAnArtifactWhoseDigestDoesNotMatchTheImmutableReleaseIndex() throws Exception {
    Path project = root.resolve("module");
    Path jar = project.resolve("build/libs/sea-module-example-1.2.3.jar");
    Files.createDirectories(jar.getParent());
    Files.writeString(jar, "built module binary");
    Files.createDirectories(project.resolve("releases"));
    Files.writeString(project.resolve("releases/index.yaml"), index("b".repeat(64)));

    var locator =
        new FileSystemLocalModuleProjectReleaseLocator(
            new FileSystemLocalArtifactInspection(java.util.List.of(root)));

    assertThatThrownBy(() -> locator.resolve(project.toString(), "sea-module-example", "1.2.3"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Built module artifact digest does not match releases/index.yaml");
  }

  private static String digest(Path file) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
  }

  private static String index(String digest) {
    return """
                schemaVersion: 1
                moduleId: sea-module-example
                releases:
                  - version: 1.2.3
                    releaseTag: v1.2.3
                    artifact:
                      groupId: org.example
                      artifactId: sea-module-example
                      version: 1.2.3
                      sha256: %s
                    source:
                      repository: https://github.com/example/sea-module-example
                      license: Apache-2.0
                    compatibility:
                      seaRuntime: ">=1.0.0 <2.0.0"
                    security:
                      permissions: []
                """
        .formatted(digest);
  }
}
