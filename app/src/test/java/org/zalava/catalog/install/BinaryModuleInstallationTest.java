package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemBinaryArtifactInstallation;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemModuleEnablement;
import org.zalava.catalog.install.application.DefaultBinaryModuleInstallation;
import org.zalava.catalog.install.application.port.in.BinaryModuleInstallation;
import org.zalava.catalog.install.application.port.out.ModuleEnablement;

class BinaryModuleInstallationTest {

  @TempDir Path workspace;

  @Test
  void installsBinaryArtifactUnderManagedRootAndEnablesModule() throws Exception {
    Path artifact = workspace.resolve("incoming/sea-module-files-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, "binary module");
    String digest = digest(artifact);
    FileSystemModuleEnablement enablement = new FileSystemModuleEnablement(workspace);
    BinaryModuleInstallation installation =
        new DefaultBinaryModuleInstallation(
            new FileSystemBinaryArtifactInstallation(workspace), enablement);

    BinaryModuleInstallation.InstalledBinaryModule installed =
        installation.install(
            new BinaryModuleInstallRequest(module(), artifact.toString(), digest, "local-private"));

    Path installedArtifact =
        workspace.resolve(
            "source-module-installation/modules/sea-module-files/1.0.0/sea-module-files-1.0.0.jar");
    assertThat(installed)
        .isEqualTo(
            new BinaryModuleInstallation.InstalledBinaryModule(
                "sea-module-files",
                "1.0.0",
                "local-private",
                installedArtifact.toString(),
                digest,
                workspace.resolve("source-module-installation/enabled-modules.json").toString()));
    assertThat(Files.readString(installedArtifact)).isEqualTo("binary module");
    assertThat(enablement.enabledModules())
        .containsExactly(
            new ModuleEnablement.EnabledModule(
                "sea-module-files",
                "1.0.0",
                installedArtifact.toString(),
                digest,
                ">=0.1.0",
                "https://github.com/example/sea-module-files.git",
                "Apache-2.0",
                "local-private",
                List.of("file.read")));
  }

  @Test
  void rejectsBinaryArtifactWithUnexpectedDigestBeforeEnablement() throws Exception {
    Path artifact = workspace.resolve("incoming/sea-module-files-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, "binary module");
    FileSystemModuleEnablement enablement = new FileSystemModuleEnablement(workspace);
    BinaryModuleInstallation installation =
        new DefaultBinaryModuleInstallation(
            new FileSystemBinaryArtifactInstallation(workspace), enablement);

    assertThatThrownBy(
            () ->
                installation.install(
                    new BinaryModuleInstallRequest(
                        module(),
                        artifact.toString(),
                        "sha256:0000000000000000000000000000000000000000000000000000000000000000",
                        "local-private")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("digest does not match");
    assertThat(enablement.enabledModules()).isEmpty();
  }

  @Test
  void installsLocalPrivateArtifactWithoutSourceCloneProvenance() throws Exception {
    Path artifact = workspace.resolve("incoming/sea-module-files-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, "binary module");
    FileSystemModuleEnablement enablement = new FileSystemModuleEnablement(workspace);
    BinaryModuleInstallation installation =
        new DefaultBinaryModuleInstallation(
            new FileSystemBinaryArtifactInstallation(workspace), enablement);

    installation.install(
        new BinaryModuleInstallRequest(
            moduleWithoutSource(), artifact.toString(), digest(artifact), "local-private"));

    ModuleEnablement.EnabledModule enabled = enablement.enabledModules().getFirst();
    assertThat(enabled.sourceRepository()).isNull();
    assertThat(enabled.binaryRepositoryId()).isEqualTo("local-private");
  }

  @Test
  void rejectsNonLocalBinaryInstallWithoutSourceProvenance() throws Exception {
    Path artifact = workspace.resolve("incoming/sea-module-files-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, "binary module");
    BinaryModuleInstallation installation =
        new DefaultBinaryModuleInstallation(
            new FileSystemBinaryArtifactInstallation(workspace),
            new FileSystemModuleEnablement(workspace));

    assertThatThrownBy(
            () ->
                installation.install(
                    new BinaryModuleInstallRequest(
                        moduleWithoutSource(),
                        artifact.toString(),
                        digest(artifact),
                        "maven-central")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Binary module source metadata is required");
  }

  @Test
  void rejectsBinaryArtifactSymlink() throws Exception {
    Path target = workspace.resolve("target.jar");
    Path link = workspace.resolve("module.jar");
    Files.writeString(target, "binary module");
    Files.createSymbolicLink(link, target);
    BinaryModuleInstallation installation =
        new DefaultBinaryModuleInstallation(
            new FileSystemBinaryArtifactInstallation(workspace),
            new FileSystemModuleEnablement(workspace));

    assertThatThrownBy(
            () ->
                installation.install(
                    new BinaryModuleInstallRequest(
                        module(), link.toString(), digest(target), "local-private")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Binary artifact must be a regular file");
  }

  private static SourceModuleIndex.Module module() {
    return new SourceModuleIndex.Module(
        "sea-module-files",
        "1.0.0",
        "Files",
        "Workspace-bound file operations",
        URI.create("https://github.com/example/sea-module-files"),
        new SourceModuleIndex.Artifact("ai.sea.modules", "sea-module-files", "1.0.0"),
        new SourceModuleIndex.Source(
            URI.create("https://github.com/example/sea-module-files.git"), "Apache-2.0"),
        new SourceModuleIndex.Build(List.of("./gradlew", "build"), List.of("./gradlew", "test")),
        new SourceModuleIndex.Compatibility(">=0.1.0"),
        Map.of("type", "object"),
        List.of(new SourceModuleIndex.Factory("workspace-files", "filesystem-root")),
        List.of(
            new SourceModuleIndex.Operation(
                "readFile", "Read file", false, Map.of("type", "object"))),
        new SourceModuleIndex.Security(List.of("file.read")));
  }

  private static SourceModuleIndex.Module moduleWithoutSource() {
    SourceModuleIndex.Module module = module();
    return new SourceModuleIndex.Module(
        module.moduleId(),
        module.version(),
        module.displayName(),
        module.description(),
        module.supportUrl(),
        module.artifact(),
        null,
        module.build(),
        module.compatibility(),
        module.configurationSchema(),
        module.factories(),
        module.operations(),
        module.security());
  }

  private static String digest(Path path) throws Exception {
    return "sha256:"
        + HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
  }
}
