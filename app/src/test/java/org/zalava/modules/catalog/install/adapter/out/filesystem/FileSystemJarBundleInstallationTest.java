package org.zalava.modules.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.BinaryArtifactInstallation;

class FileSystemJarBundleInstallationTest {
  @TempDir Path workspace;

  @Test
  void verifiesAndExtractsDeclaredModuleAndRuntimeJarsFromOneDownload() throws Exception {
    Path bundle = bundle(false);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    BinaryArtifactInstallation.InstalledBundle installed =
        installation.installBundle(install(bundle));

    assertThat(installed.artifacts()).hasSize(2);
    assertThat(Path.of(installed.artifacts().getFirst().path())).hasContent("module");
    assertThat(Path.of(installed.artifacts().get(1).path())).hasContent("runtime");
    assertThat(installed.artifacts().get(1).path()).contains("/lib/runtime.jar");
  }

  @Test
  void rejectsAnUndeclaredArchiveMemberWithoutPublishingTheBundle() throws Exception {
    Path bundle = bundle(true);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("undeclared member");
    assertThat(workspace.resolve("source-module-installation/modules/example/1.0.0"))
        .doesNotExist();
  }

  @Test
  void verifiesReleasedSeaManifestWithTheSameDigestAndMemberRules() throws Exception {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    var installed =
        installation.installBundle(install(bundle(false, "META-INF/sea-module-bundle.yaml")));
    assertThat(installed.artifacts()).hasSize(2);
    assertThat(Path.of(installed.artifacts().getFirst().path())).hasContent("module");
    installation.discard(installed);
    assertThat(Path.of(installed.artifacts().getFirst().path()).getParent()).doesNotExist();
    assertThatThrownBy(
            () ->
                installation.installBundle(
                    install(bundle(true, "META-INF/sea-module-bundle.yaml"))))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("undeclared member");
  }

  @Test
  void rejectsAmbiguousCurrentAndReleasedManifestsWithoutPublishing() throws Exception {
    Path bundle = bundle(false, "both");
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("exactly one manifest");
    assertThat(workspace.resolve("source-module-installation/modules/example/1.0.0"))
        .doesNotExist();
  }

  private BinaryArtifactInstallation.Install install(Path bundle) throws Exception {
    return new BinaryArtifactInstallation.Install(
        "example",
        new SourceModuleIndex.Artifact("org.example", "example-bundle", "1.0.0"),
        bundle.toString(),
        digest(Files.readAllBytes(bundle)));
  }

  private Path bundle(boolean extraMember) throws Exception {
    return bundle(extraMember, "META-INF/zalava-module-bundle.yaml");
  }

  private Path bundle(boolean extraMember, String manifestEntry) throws Exception {
    byte[] module = "module".getBytes(StandardCharsets.UTF_8);
    byte[] runtime = "runtime".getBytes(StandardCharsets.UTF_8);
    String manifest =
        """
        module:
          path: module.jar
          sha256: %s
        runtime:
          - path: lib/runtime.jar
            sha256: %s
        """
            .formatted(digest(module).substring(7), digest(runtime).substring(7));
    Path bundle = workspace.resolve("bundle.jar");
    try (OutputStream output = Files.newOutputStream(bundle);
        JarOutputStream archive = new JarOutputStream(output)) {
      if (manifestEntry.equals("both")) {
        entry(
            archive,
            "META-INF/zalava-module-bundle.yaml",
            manifest.getBytes(StandardCharsets.UTF_8));
        entry(
            archive, "META-INF/sea-module-bundle.yaml", manifest.getBytes(StandardCharsets.UTF_8));
      } else {
        entry(archive, manifestEntry, manifest.getBytes(StandardCharsets.UTF_8));
      }
      entry(archive, "module.jar", module);
      entry(archive, "lib/runtime.jar", runtime);
      if (extraMember) {
        entry(archive, "lib/unexpected.jar", "unexpected".getBytes(StandardCharsets.UTF_8));
      }
    }
    return bundle;
  }

  private static void entry(JarOutputStream archive, String name, byte[] contents)
      throws Exception {
    archive.putNextEntry(new JarEntry(name));
    archive.write(contents);
    archive.closeEntry();
  }

  private static String digest(byte[] contents) throws Exception {
    return "sha256:"
        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
  }
}
