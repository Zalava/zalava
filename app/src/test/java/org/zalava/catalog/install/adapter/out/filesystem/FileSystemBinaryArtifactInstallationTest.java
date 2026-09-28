package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.BinaryArtifactInstallation.Install;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemBinaryArtifactInstallationTest {

  @TempDir Path workspace;

  @Test
  void installValidArtifact() throws Exception {
    byte[] content = "test-jar".getBytes();
    String digest = sha256(content);
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var artifact = new SourceModuleIndex.Artifact("ai.sea.modules", "test", "1.0.0");
    var result =
        installation.install(new Install("test-module", artifact, source.toString(), digest));
    assertThat(result.path()).contains("test").contains("1.0.0");
    assertThat(result.digest()).isEqualTo(digest);
  }

  @Test
  void rejectsNullSourcePath() {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var artifact = new SourceModuleIndex.Artifact("ai.sea.modules", "test", "1.0.0");
    assertThatThrownBy(
            () -> installation.install(new Install("test-module", artifact, null, "sha256:x")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("required");
  }

  @Test
  void rejectsInvalidModuleId() throws Exception {
    byte[] content = "test".getBytes();
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var artifact = new SourceModuleIndex.Artifact("ai.sea.modules", "test", "1.0.0");
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install("bad/id", artifact, source.toString(), sha256(content))))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Invalid module id");
  }

  @Test
  void rejectsDigestMismatch() throws Exception {
    byte[] content = "test".getBytes();
    Path source = workspace.resolve("source.jar");
    Files.write(source, content);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var artifact = new SourceModuleIndex.Artifact("ai.sea.modules", "test", "1.0.0");
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install("test-module", artifact, source.toString(), "sha256:wrong")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("does not match expected digest");
  }

  @Test
  void rejectsSourceOutsideTrustedRoot() {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var artifact = new SourceModuleIndex.Artifact("ai.sea.modules", "test", "1.0.0");
    assertThatThrownBy(
            () ->
                installation.install(
                    new Install("test-module", artifact, "/tmp/outside.jar", "sha256:x")))
        .isInstanceOf(SourceModuleInstallationException.class);
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
