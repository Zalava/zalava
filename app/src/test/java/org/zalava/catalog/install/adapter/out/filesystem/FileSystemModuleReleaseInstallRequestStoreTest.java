package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.zalava.catalog.ModuleReleaseInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.development.CandidateEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemModuleReleaseInstallRequestStoreTest {

  @TempDir Path workspace;

  private static SourceModuleIndex.Module testModule() {
    return new SourceModuleIndex.Module(
        "test-module",
        "1.0.0",
        "Test",
        "desc",
        URI.create("https://example.com"),
        new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
        new SourceModuleIndex.Source(URI.create("https://github.com/x"), "MIT"),
        new SourceModuleIndex.Build(List.of("echo"), List.of()),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
  }

  private static ModuleReleaseInstallRequest validRequest(String id) {
    return new ModuleReleaseInstallRequest(
        id,
        Instant.parse("2025-01-01T00:00:00Z"),
        URI.create("https://example.com/manifest.yaml"),
        testModule(),
        "/tmp/artifact.jar",
        "sha256:abc",
        "maven-central",
        null,
        0,
        CandidateEvaluation.Decision.ACCEPTED,
        ModuleReleaseInstallRequest.Status.PENDING,
        null,
        null);
  }

  @Test
  void createAndGetRequest() {
    var store = new FileSystemModuleReleaseInstallRequestStore(workspace);
    var req = store.create(validRequest("rel-1"));
    assertThat(req.requestId()).isEqualTo("rel-1");
    assertThat(store.get("rel-1").requestId()).isEqualTo("rel-1");
  }

  @Test
  void recentReturnsSorted() {
    var store = new FileSystemModuleReleaseInstallRequestStore(workspace);
    store.create(
        new ModuleReleaseInstallRequest(
            "r1",
            Instant.parse("2025-01-01T00:00:00Z"),
            URI.create("https://example.com/manifest.yaml"),
            testModule(),
            "/tmp/a.jar",
            "sha256:aaa",
            "repo",
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            ModuleReleaseInstallRequest.Status.PENDING,
            null,
            null));
    store.create(
        new ModuleReleaseInstallRequest(
            "r2",
            Instant.parse("2025-06-01T00:00:00Z"),
            URI.create("https://example.com/manifest.yaml"),
            testModule(),
            "/tmp/b.jar",
            "sha256:bbb",
            "repo",
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            ModuleReleaseInstallRequest.Status.PENDING,
            null,
            null));
    var recent = store.recent(10);
    assertThat(recent).hasSize(2);
    assertThat(recent.getFirst().requestId()).isEqualTo("r2");
  }

  @Test
  void recentRejectsInvalidLimit() {
    var store = new FileSystemModuleReleaseInstallRequestStore(workspace);
    assertThatThrownBy(() -> store.recent(0))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("between 1 and 100");
    assertThatThrownBy(() -> store.recent(101))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("between 1 and 100");
  }

  @Test
  void getRejectsInvalidId() {
    var store = new FileSystemModuleReleaseInstallRequestStore(workspace);
    assertThatThrownBy(() -> store.get("bad..id"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("invalid request id");
  }

  @Test
  void getRejectsMissingId() {
    var store = new FileSystemModuleReleaseInstallRequestStore(workspace);
    assertThatThrownBy(() -> store.get("nonexistent"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void saveOverwritesExistingRequest() {
    var store = new FileSystemModuleReleaseInstallRequestStore(workspace);
    store.create(validRequest("ovr"));
    var updated =
        new ModuleReleaseInstallRequest(
            "ovr",
            Instant.parse("2025-06-01T00:00:00Z"),
            URI.create("https://example.com/manifest.yaml"),
            testModule(),
            "/tmp/b.jar",
            "sha256:upd",
            "repo",
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            ModuleReleaseInstallRequest.Status.SUCCEEDED,
            null,
            "updated");
    store.save(updated);
    assertThat(store.get("ovr").message()).isEqualTo("updated");
  }
}
