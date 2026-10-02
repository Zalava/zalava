package org.zalava.modules.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.modules.catalog.ModuleReleaseInstallRequest;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemModuleReleaseInstallRequestStore;

class FileSystemModuleReleaseInstallRequestStoreTest {

  @TempDir Path workspace;

  @Test
  void reloadsTerminalDecisionAndImmutableProvenanceAfterRestart() throws Exception {
    FileSystemModuleReleaseInstallRequestStore store =
        new FileSystemModuleReleaseInstallRequestStore(workspace);
    ModuleReleaseInstallRequest decided =
        store.save(
            request(
                "release-1",
                Instant.parse("2026-07-25T10:15:30Z"),
                ModuleReleaseInstallRequest.Status.SUCCEEDED,
                Instant.parse("2026-07-25T10:16:30Z"),
                "Module enabled"));

    FileSystemModuleReleaseInstallRequestStore reloaded =
        new FileSystemModuleReleaseInstallRequestStore(workspace);

    assertThat(reloaded.get("release-1")).isEqualTo(decided);
    assertThat(reloaded.get("release-1"))
        .extracting(
            ModuleReleaseInstallRequest::manifestUri,
            ModuleReleaseInstallRequest::artifactDigest,
            ModuleReleaseInstallRequest::repositoryId,
            ModuleReleaseInstallRequest::decidedAt)
        .containsExactly(
            URI.create("https://example.test/releases.yaml"),
            "sha256:" + "a".repeat(64),
            "releases",
            Instant.parse("2026-07-25T10:16:30Z"));
    assertThat(Files.list(workspace.resolve("source-module-installation/release-requests")))
        .allMatch(path -> path.getFileName().toString().endsWith(".json"));
  }

  @Test
  void listsNewestRequestsFirstWithABoundedLimit() {
    FileSystemModuleReleaseInstallRequestStore store =
        new FileSystemModuleReleaseInstallRequestStore(workspace);
    store.create(
        request(
            "release-1",
            Instant.parse("2026-07-25T10:15:30Z"),
            ModuleReleaseInstallRequest.Status.PENDING,
            null,
            "Awaiting approval"));
    store.create(
        request(
            "release-2",
            Instant.parse("2026-07-25T10:16:30Z"),
            ModuleReleaseInstallRequest.Status.DENIED,
            Instant.parse("2026-07-25T10:17:30Z"),
            "Installation denied"));

    assertThat(store.recent(1))
        .extracting(ModuleReleaseInstallRequest::requestId)
        .containsExactly("release-2");
    assertThatThrownBy(() -> store.recent(0))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("between 1 and 100");
  }

  @Test
  void returnsNoRequestsWhenTheManagedDirectoryIsAbsent() throws Exception {
    FileSystemModuleReleaseInstallRequestStore store =
        new FileSystemModuleReleaseInstallRequestStore(workspace);
    Files.delete(workspace.resolve("source-module-installation/release-requests"));

    assertThat(store.recent(10)).isEmpty();
  }

  @Test
  void rejectsRequestIdentifiersThatCouldEscapeTheManagedDirectory() {
    FileSystemModuleReleaseInstallRequestStore store =
        new FileSystemModuleReleaseInstallRequestStore(workspace);

    assertThatThrownBy(() -> store.get("../../outside"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("invalid request id");
  }

  private static ModuleReleaseInstallRequest request(
      String requestId,
      Instant createdAt,
      ModuleReleaseInstallRequest.Status status,
      Instant decidedAt,
      String message) {
    return new ModuleReleaseInstallRequest(
        requestId,
        createdAt,
        URI.create("https://example.test/releases.yaml"),
        module(),
        "/private/download.jar",
        "sha256:" + "a".repeat(64),
        "releases",
        "development-request",
        1,
        org.zalava.modules.development.CandidateEvaluation.Decision.ACCEPTED,
        status,
        decidedAt,
        message);
  }

  private static SourceModuleIndex.Module module() {
    return new SourceModuleIndex.Module(
        "zalava-module-time",
        "1.0.0",
        "Time",
        "Time tools",
        URI.create("https://github.com/example/zalava-module-time"),
        new SourceModuleIndex.Artifact("org.example", "zalava-module-time", "1.0.0"),
        new SourceModuleIndex.Source(
            URI.create("https://github.com/example/zalava-module-time"), "Apache-2.0"),
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of("time.read")));
  }
}
