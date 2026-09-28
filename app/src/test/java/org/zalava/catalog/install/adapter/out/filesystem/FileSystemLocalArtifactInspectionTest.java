package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.install.SourceModuleInstallationException;

class FileSystemLocalArtifactInspectionTest {

  @TempDir Path workspace;

  @Test
  void inspectValidJar() throws Exception {
    Path root = workspace.resolve("trusted");
    Files.createDirectories(root);
    byte[] content = "test-jar".getBytes();
    Path jar = root.resolve("module.jar");
    Files.write(jar, content);
    var inspection = new FileSystemLocalArtifactInspection(java.util.List.of(root));
    var result = inspection.inspect(jar.toString());
    assertThat(result.path()).endsWith("module.jar");
    assertThat(result.sha256Digest()).startsWith("sha256:");
  }

  @Test
  void rejectsNullPath() {
    var inspection = new FileSystemLocalArtifactInspection(java.util.List.of(workspace));
    assertThatThrownBy(() -> inspection.inspect(null))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("required");
  }

  @Test
  void rejectsBlankPath() {
    var inspection = new FileSystemLocalArtifactInspection(java.util.List.of(workspace));
    assertThatThrownBy(() -> inspection.inspect("  "))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("required");
  }

  @Test
  void rejectsNonJarFile() throws Exception {
    Path root = workspace.resolve("trusted");
    Files.createDirectories(root);
    Path file = root.resolve("module.txt");
    Files.writeString(file, "not a jar");
    var inspection = new FileSystemLocalArtifactInspection(java.util.List.of(root));
    assertThatThrownBy(() -> inspection.inspect(file.toString()))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("regular JAR file");
  }

  @Test
  void rejectsFileOutsideTrustedRoot() throws Exception {
    Path root = workspace.resolve("trusted");
    Files.createDirectories(root);
    Path other = workspace.resolve("untrusted");
    Files.createDirectories(other);
    Path jar = other.resolve("module.jar");
    Files.write(jar, "content".getBytes());
    var inspection = new FileSystemLocalArtifactInspection(java.util.List.of(root));
    assertThatThrownBy(() -> inspection.inspect(jar.toString()))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("within a trusted root");
  }

  @Test
  void rejectsSymlinkedFile() throws Exception {
    Path root = workspace.resolve("trusted");
    Files.createDirectories(root);
    Path target = workspace.resolve("real.jar");
    Files.write(target, "content".getBytes());
    Path link = root.resolve("link.jar");
    Files.createSymbolicLink(link, target);
    var inspection = new FileSystemLocalArtifactInspection(java.util.List.of(root));
    assertThatThrownBy(() -> inspection.inspect(link.toString()))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("regular JAR file");
  }
}
