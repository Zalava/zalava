package org.zalava.modules.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;

class FileSystemLocalArtifactInspectionAdditionalTest {

  @TempDir Path workspace;

  @Test
  void inspectValidJar() throws Exception {
    Path root = workspace.resolve("trusted");
    Files.createDirectories(root);
    byte[] content = "test-jar".getBytes();
    Path jar = root.resolve("module.jar");
    Files.write(jar, content);
    var inspection = new FileSystemLocalArtifactInspection(List.of(root));
    var result = inspection.inspect(jar.toString());
    assertThat(result.path()).endsWith("module.jar");
    assertThat(result.sha256Digest()).startsWith("sha256:");
  }

  @Test
  void rejectsEmptyPath() {
    var inspection = new FileSystemLocalArtifactInspection(List.of(workspace));
    assertThatThrownBy(() -> inspection.inspect("  "))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("required");
  }

  @Test
  void rejectsNonJarExtension() throws Exception {
    Path root = workspace.resolve("trusted");
    Files.createDirectories(root);
    Path file = root.resolve("module.txt");
    Files.writeString(file, "content");
    var inspection = new FileSystemLocalArtifactInspection(List.of(root));
    assertThatThrownBy(() -> inspection.inspect(file.toString()))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("regular JAR file");
  }

  @Test
  void rejectsNonExistentFile() {
    var inspection = new FileSystemLocalArtifactInspection(List.of(workspace));
    assertThatThrownBy(() -> inspection.inspect("/tmp/nonexistent.jar"))
        .isInstanceOf(SourceModuleInstallationException.class);
  }

  @Test
  void rejectsFileOutsideTrustedRoot() throws Exception {
    Path root = workspace.resolve("trusted");
    Files.createDirectories(root);
    Path other = workspace.resolve("untrusted");
    Files.createDirectories(other);
    Path jar = other.resolve("module.jar");
    Files.write(jar, "content".getBytes());
    var inspection = new FileSystemLocalArtifactInspection(List.of(root));
    assertThatThrownBy(() -> inspection.inspect(jar.toString()))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("within a trusted root");
  }

  @Test
  void rejectsSymlinkedJar() throws Exception {
    Path root = workspace.resolve("trusted");
    Files.createDirectories(root);
    Path target = workspace.resolve("real.jar");
    Files.write(target, "content".getBytes());
    Path link = root.resolve("link.jar");
    Files.createSymbolicLink(link, target);
    var inspection = new FileSystemLocalArtifactInspection(List.of(root));
    assertThatThrownBy(() -> inspection.inspect(link.toString()))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("regular JAR file");
  }

  @Test
  void multipleTrustedRoots() throws Exception {
    Path root1 = workspace.resolve("root1");
    Path root2 = workspace.resolve("root2");
    Files.createDirectories(root1);
    Files.createDirectories(root2);
    byte[] content = "content".getBytes();
    Path jar1 = root1.resolve("a.jar");
    Path jar2 = root2.resolve("b.jar");
    Files.write(jar1, content);
    Files.write(jar2, content);
    var inspection = new FileSystemLocalArtifactInspection(List.of(root1, root2));
    assertThat(inspection.inspect(jar1.toString())).isNotNull();
    assertThat(inspection.inspect(jar2.toString())).isNotNull();
  }
}
