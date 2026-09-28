package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.ModuleEnablement.EnabledModule;

class FileSystemModuleEnablementTest {

  @TempDir Path workspace;

  @Test
  void enabledModulesReturnsEmptyWhenRegistryMissing() {
    var enablement = new FileSystemModuleEnablement(workspace);
    assertThat(enablement.enabledModules()).isEmpty();
  }

  @Test
  void enabledModulesRejectsCorruptRegistry() throws Exception {
    Path registry = workspace.resolve("source-module-installation/enabled-modules.json");
    Files.createDirectories(registry.getParent());
    Files.writeString(registry, "not-json");
    var enablement = new FileSystemModuleEnablement(workspace);
    assertThatThrownBy(enablement::enabledModules)
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Unable to read enabled module registry");
  }

  @Test
  void enableRejectsArtifactOutsideManagedRoot() {
    var enablement = new FileSystemModuleEnablement(workspace);
    var module =
        new EnabledModule(
            "test-module",
            "1.0.0",
            "/tmp/outside/artifact.jar",
            "sha256:abc",
            ">=1.0.0",
            null,
            null,
            null,
            List.of());
    assertThatThrownBy(() -> enablement.enable(module))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("must be inside the managed module directory");
  }

  @Test
  void enableRejectsDigestMismatch() throws Exception {
    Path modulesRoot = workspace.resolve("source-module-installation/modules");
    Files.createDirectories(modulesRoot);
    Path artifact = modulesRoot.resolve("test.jar");
    Files.writeString(artifact, "content");
    var enablement = new FileSystemModuleEnablement(workspace);
    var module =
        new EnabledModule(
            "test-module",
            "1.0.0",
            artifact.toAbsolutePath().toString(),
            "sha256:wrong",
            ">=1.0.0",
            null,
            null,
            null,
            List.of());
    assertThatThrownBy(() -> enablement.enable(module))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("digest does not match");
  }

  @Test
  void enableAcceptsValidArtifactAndPersists() throws Exception {
    byte[] content = "valid-artifact".getBytes();
    String digest =
        "sha256:"
            + java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
    Path modulesRoot = workspace.resolve("source-module-installation/modules");
    Files.createDirectories(modulesRoot);
    Path artifact = modulesRoot.resolve("valid.jar");
    Files.write(artifact, content);
    var enablement = new FileSystemModuleEnablement(workspace);
    var module =
        new EnabledModule(
            "test-module",
            "1.0.0",
            artifact.toAbsolutePath().toString(),
            digest,
            ">=1.0.0",
            "repo",
            "MIT",
            "curated",
            List.of());
    var result = enablement.enable(module);
    assertThat(result.registryPath()).contains("enabled-modules.json");
    assertThat(enablement.enabledModules()).hasSize(1);
    assertThat(enablement.enabledModules().getFirst().moduleId()).isEqualTo("test-module");
  }

  @Test
  void disableRemovesAnEnabledModuleAndPersists() throws Exception {
    var enablement = new FileSystemModuleEnablement(workspace);
    enable(enablement);

    var result = enablement.disable("test-module");

    assertThat(result.changed()).isTrue();
    assertThat(enablement.enabledModules()).isEmpty();
  }

  @Test
  void disableIsIdempotentWhenTheModuleIsNotEnabled() {
    var enablement = new FileSystemModuleEnablement(workspace);

    var result = enablement.disable("missing-module");

    assertThat(result.changed()).isFalse();
    assertThat(enablement.enabledModules()).isEmpty();
  }

  @Test
  void disableRemovesAModuleWhoseArtifactIsMissing() throws Exception {
    var enablement = new FileSystemModuleEnablement(workspace);
    Path artifact = enable(enablement);
    Files.delete(artifact);

    var result = enablement.disable("test-module");

    assertThat(result.changed()).isTrue();
    assertThat(enablement.enabledModules()).isEmpty();
  }

  private Path enable(FileSystemModuleEnablement enablement) throws Exception {
    byte[] content = "valid-artifact".getBytes();
    String digest =
        "sha256:"
            + java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
    Path modulesRoot = workspace.resolve("source-module-installation/modules");
    Files.createDirectories(modulesRoot);
    Path artifact = modulesRoot.resolve("valid.jar");
    Files.write(artifact, content);
    enablement.enable(
        new EnabledModule(
            "test-module",
            "1.0.0",
            artifact.toAbsolutePath().toString(),
            digest,
            ">=1.0.0",
            "repo",
            "MIT",
            "curated",
            List.of()));
    return artifact;
  }
}
