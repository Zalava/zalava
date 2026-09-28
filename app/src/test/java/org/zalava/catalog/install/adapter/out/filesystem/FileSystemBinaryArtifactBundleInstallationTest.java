package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.BinaryArtifactInstallation;

class FileSystemBinaryArtifactBundleInstallationTest {
  @TempDir Path workspace;

  @Test
  void installsAllVerifiedArtifactsAsOneManagedBundle() throws Exception {
    Path primary = artifact("module");
    Path runtime = artifact("runtime");
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    BinaryArtifactInstallation.InstalledBundle bundle =
        installation.install(
            new BinaryArtifactInstallation.BundleInstall(
                List.of(
                    install("sea-module-example", "sea-module-example", "1.0.0", primary),
                    install("sea-module-example", "dependency", "2.0.0", runtime))));

    assertThat(bundle.artifacts()).hasSize(2);
    assertThat(bundle.artifacts())
        .allSatisfy(artifact -> assertThat(artifact.path()).contains("modules"));
    assertThat(Path.of(bundle.artifacts().get(0).path())).isRegularFile();
    assertThat(Path.of(bundle.artifacts().get(1).path())).isRegularFile();
  }

  @Test
  void rejectsABadSecondaryDigestWithoutPublishingAStagingDirectory() throws Exception {
    Path primary = artifact("module");
    Path runtime = artifact("runtime");
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(
            () ->
                installation.install(
                    new BinaryArtifactInstallation.BundleInstall(
                        List.of(
                            install("sea-module-example", "sea-module-example", "1.0.0", primary),
                            new BinaryArtifactInstallation.Install(
                                "sea-module-example",
                                coordinate("dependency", "2.0.0"),
                                runtime.toString(),
                                "sha256:" + "0".repeat(64))))))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("digest");

    Path moduleRoot = workspace.resolve("source-module-installation/modules/sea-module-example");
    assertThat(moduleRoot.resolve("1.0.0")).doesNotExist();
    assertThat(Files.list(moduleRoot).map(path -> path.getFileName().toString()))
        .noneMatch(name -> name.contains(".staging-"));
  }

  private BinaryArtifactInstallation.Install install(
      String moduleId, String artifactId, String version, Path source) {
    return new BinaryArtifactInstallation.Install(
        moduleId, coordinate(artifactId, version), source.toString(), digest(source));
  }

  private static SourceModuleIndex.Artifact coordinate(String artifactId, String version) {
    return new SourceModuleIndex.Artifact("org.example", artifactId, version);
  }

  private Path artifact(String contents) throws Exception {
    Path artifact = workspace.resolve(contents + ".jar");
    Files.writeString(artifact, contents);
    return artifact;
  }

  private static String digest(Path path) {
    try {
      return "sha256:"
          + HexFormat.of()
              .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
