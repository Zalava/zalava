package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.ModuleEnablement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemModuleBundleEnablementTest {
  @TempDir Path workspace;

  @Test
  void persistsAndVerifiesOrderedRuntimeArtifactsAlongsideLegacyPrimaryFields() throws Exception {
    Path directory = workspace.resolve("source-module-installation/modules/example/1.0.0");
    Files.createDirectories(directory);
    Path primary = directory.resolve("example-1.0.0.jar");
    Path runtime = directory.resolve("dependency-2.0.0.jar");
    Files.writeString(primary, "primary");
    Files.writeString(runtime, "runtime");
    var enablement = new FileSystemModuleEnablement(workspace);

    enablement.enable(
        new ModuleEnablement.EnabledModule(
            "example",
            "1.0.0",
            primary.toString(),
            sha256("primary"),
            ">=1.0.0",
            null,
            null,
            "maven-central",
            List.of(),
            List.of(new ModuleEnablement.RuntimeArtifact(runtime.toString(), sha256("runtime")))));

    assertThat(enablement.enabledModules().getFirst().runtimeArtifacts())
        .extracting(ModuleEnablement.RuntimeArtifact::artifactPath)
        .containsExactly(runtime.toAbsolutePath().toString());
    Files.writeString(runtime, "tampered");

    assertThatThrownBy(enablement::enabledModules)
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("digest");
  }

  private static String sha256(String value) {
    try {
      return "sha256:"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
