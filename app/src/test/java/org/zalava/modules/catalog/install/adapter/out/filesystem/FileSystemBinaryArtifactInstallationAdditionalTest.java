package org.zalava.modules.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.BinaryArtifactInstallation.Install;

class FileSystemBinaryArtifactInstallationAdditionalTest {

  @TempDir Path workspace;

  private static SourceModuleIndex.Artifact artifact() {
    return new SourceModuleIndex.Artifact("ai.zalava.modules", "test", "1.0.0");
  }

  @Test
  void installValidArtifact() throws Exception {
    byte[] content = "test-jar".getBytes();
    String digest = sha256(content);
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var result =
        installation.install(new Install("test-module", artifact(), source.toString(), digest));
    assertThat(result.path()).contains("test").contains("1.0.0");
    assertThat(result.digest()).isEqualTo(digest);
  }

  @Test
  void rejectsNullArtifactPath() {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install(
                        "m",
                        artifact(),
                        null,
                        "sha256:abababababababababababababababababababababababababababababababab")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("required");
  }

  @Test
  void rejectsArtifactNotAFile() {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install(
                        "m",
                        artifact(),
                        "/tmp/nonexistent.jar",
                        "sha256:abababababababababababababababababababababababababababababababab")))
        .isInstanceOf(SourceModuleInstallationException.class);
  }

  @Test
  void rejectsSymlinkedArtifact() throws Exception {
    Path target = workspace.resolve("real.jar");
    Files.write(target, "content".getBytes());
    Path link = workspace.resolve("link.jar");
    Files.createSymbolicLink(link, target);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install(
                        "m",
                        artifact(),
                        link.toString(),
                        "sha256:abababababababababababababababababababababababababababababababab")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("regular file");
  }

  @Test
  void rejectsDigestMismatch() throws Exception {
    byte[] content = "test".getBytes();
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install(
                        "m",
                        artifact(),
                        source.toString(),
                        "sha256:0000000000000000000000000000000000000000000000000000000000000000")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("does not match expected digest");
  }

  @Test
  void rejectsInvalidModuleId() throws Exception {
    byte[] content = "test".getBytes();
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install("bad/id", artifact(), source.toString(), sha256(content))))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Invalid module id");
  }

  @Test
  void rejectsInvalidArtifactId() throws Exception {
    byte[] content = "test".getBytes();
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var badArtifact = new SourceModuleIndex.Artifact("g", "bad/id", "1.0.0");
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install("m", badArtifact, source.toString(), sha256(content))))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Invalid artifact id");
  }

  @Test
  void rejectsInvalidArtifactVersion() throws Exception {
    byte[] content = "test".getBytes();
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var badArtifact = new SourceModuleIndex.Artifact("g", "a", "bad/version");
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install("m", badArtifact, source.toString(), sha256(content))))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Invalid artifact version");
  }

  @Test
  void installOverwritesExistingArtifact() throws Exception {
    Path modulesRoot = workspace.resolve("source-module-installation/modules");
    Files.createDirectories(modulesRoot.resolve("m/1.0.0"));
    Files.writeString(modulesRoot.resolve("m/1.0.0/test-1.0.0.jar"), "old");
    byte[] content = "new-jar".getBytes();
    String digest = sha256(content);
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var result = installation.install(new Install("m", artifact(), source.toString(), digest));
    assertThat(result.digest()).isEqualTo(digest);
  }

  private static String sha256(byte[] data) {
    try {
      return "sha256:"
          + java.util.HexFormat.of()
              .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
