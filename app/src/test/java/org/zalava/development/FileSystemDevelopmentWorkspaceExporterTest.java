package org.zalava.development;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.zalava.development.adapter.out.filesystem.FileSystemDevelopmentRequestStore;
import org.zalava.development.adapter.out.filesystem.FileSystemDevelopmentWorkspaceExporter;
import org.zalava.development.application.DefaultDevelopmentWorkspaceExport;

class FileSystemDevelopmentWorkspaceExporterTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exportsACompleteSelfContainedPackageAndSafelyReexportsIt() throws Exception {
    FileSystemDevelopmentRequestStore requests =
        new FileSystemDevelopmentRequestStore(
            new FileSystemResource(temporaryDirectory.resolve("sea-state")));
    DevelopmentRequestId requestId = new DevelopmentRequestId("request-export-1");
    requests.save(
        new ModuleDevelopmentRequest(
            requestId,
            Instant.parse("2026-07-25T12:00:00Z"),
            DevelopmentRequestStatus.PREPARED,
            List.of(
                new ModuleDevelopmentRequest.Revision(
                    1,
                    Instant.parse("2026-07-25T12:00:00Z"),
                    "Initial request",
                    ModuleDevelopmentRequestTest.contract("0.1.0")))));
    DefaultDevelopmentWorkspaceExport exporter =
        new DefaultDevelopmentWorkspaceExport(
            requests, new FileSystemDevelopmentWorkspaceExporter());
    Path firstWorkspace = temporaryDirectory.resolve("external-workspace").toAbsolutePath();

    DevelopmentWorkspace exported = exporter.export(requestId, firstWorkspace.toString());

    Path request = firstWorkspace.resolve(".sea-request");
    assertThat(exported.sha256().get("development-contract.yaml")).matches("[0-9a-f]{64}");
    assertThat(exported.sha256().get("acceptance-scenarios.yaml")).matches("[0-9a-f]{64}");
    assertThat(request.resolve("request.json")).exists();
    assertThat(request.resolve("development-contract.yaml")).exists();
    assertThat(request.resolve("acceptance-scenarios.yaml")).exists();
    assertThat(request.resolve("sdk/module-api.jar")).doesNotExist();
    assertThat(request.resolve("sdk/module-api.coordinates"))
        .hasContent("org.zalava:module-api:1.0\n");
    assertThat(request.resolve("docs/module-api-release-guide.md")).exists();
    assertThat(request.resolve("schemas/tool-contract.schema.json")).exists();
    assertThat(request.resolve("schemas/module-metadata.schema.yaml")).exists();
    assertThat(request.resolve("examples/minimal-module/build.gradle")).exists();
    assertThat(request.resolve("examples/minimal-module/module-metadata.yaml"))
        .content()
        .contains("source:", "license: Apache-2.0");
    assertThat(request.resolve("schemas/module-metadata.schema.yaml"))
        .content()
        .contains("does not require source-clone provenance");
    assertThat(request.resolve("CODEX_TASK.md"))
        .content()
        .contains("Read this entire", "Do not edit the exported contract");
    assertThat(request.resolve("traceability.json"))
        .content()
        .contains("SEA persisted development request");
    assertThat(firstWorkspace.resolve("delivery")).isDirectory();
    assertThat(requests.get(requestId).status()).isEqualTo(DevelopmentRequestStatus.EXPORTED);

    Path userImplementation = firstWorkspace.resolve("src/UserOwnedModule.java");
    Files.createDirectories(userImplementation.getParent());
    Files.writeString(userImplementation, "// user-owned implementation");
    Files.delete(request.resolve("README.md"));

    exporter.export(requestId, firstWorkspace.toString());

    assertThat(request.resolve("README.md")).exists();
    assertThat(userImplementation).hasContent("// user-owned implementation");

    Path secondWorkspace = temporaryDirectory.resolve("another-user-workspace").toAbsolutePath();
    assertThat(exporter.export(requestId, secondWorkspace.toString()).root())
        .isEqualTo(secondWorkspace.toString());
    assertThat(secondWorkspace.resolve(".sea-request/CODEX_TASK.md")).exists();
  }
}
