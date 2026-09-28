package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.ModuleReleaseInstallRequest;
import org.zalava.catalog.install.application.port.in.UploadedModuleInstallation;

/** Covers bundle detection for approval-gated administrator JAR uploads. */
class DefaultUploadedModuleInstallationTest {

  private static final String METADATA =
      """
      schemaVersion: 1
      modules:
        - moduleId: sea-module-example
          version: 1.0.0
          displayName: Example
          description: Example module.
          supportUrl: https://github.com/Zalava/zalava-module-example
          artifact:
            groupId: org.zalava.modules
            artifactId: sea-module-example
            version: 1.0.0
          compatibility:
            seaRuntime: ">=1.0.0"
          configurationSchema:
            type: object
          factories:
            - factoryId: example
              providerType: example
          operations: []
          security:
            permissions: []
      """;

  @TempDir Path workspace;

  @Test
  void treatsAPlainModuleJarAsASingleArtifact() throws Exception {
    Path jar = jar(Map.of("module-metadata.yaml", METADATA.getBytes()));

    ModuleReleaseInstallRequest request = create(jar);

    assertThat(request.artifactBundle()).isFalse();
    assertThat(request.runtimeArtifacts()).isEmpty();
    assertThat(request.module().moduleId()).isEqualTo("sea-module-example");
    assertThat(request.repositoryId()).isEqualTo("local-upload");
  }

  @Test
  void detectsABundleJarAndMarksTheRequestAsABundle() throws Exception {
    Map<String, byte[]> members = new LinkedHashMap<>();
    members.put("module-metadata.yaml", METADATA.getBytes());
    members.put("META-INF/sea-module-bundle.yaml", "module:\n  path: module.jar\n".getBytes());
    members.put("module.jar", "module-bytes".getBytes());
    Path jar = jar(members);

    ModuleReleaseInstallRequest request = create(jar);

    assertThat(request.artifactBundle()).isTrue();
    assertThat(request.runtimeArtifacts()).isEmpty();
    assertThat(request.module().moduleId()).isEqualTo("sea-module-example");
  }

  private ModuleReleaseInstallRequest create(Path jar) throws IOException {
    var installation =
        new DefaultUploadedModuleInstallation(
            workspace,
            new FileSystemModuleReleaseInstallRequestStore(workspace),
            Clock.systemUTC());
    try (InputStream content = Files.newInputStream(jar)) {
      return installation.create(new UploadedModuleInstallation.Request("uploaded.jar", content));
    }
  }

  private Path jar(Map<String, byte[]> members) throws IOException {
    Path path = workspace.resolve("uploaded-" + System.nanoTime() + ".jar");
    try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(path))) {
      for (Map.Entry<String, byte[]> member : members.entrySet()) {
        out.putNextEntry(new java.util.jar.JarEntry(member.getKey()));
        out.write(member.getValue());
        out.closeEntry();
      }
    }
    return path;
  }
}
