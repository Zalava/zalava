package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.development.CandidateEvaluation;

class FileSystemLocalArtifactInstallRequestStoreTest {

  @TempDir Path workspace;

  private static SourceModuleIndex.Module testModule() {
    return new SourceModuleIndex.Module(
        "test-module",
        "1.0.0",
        "Test Module",
        "A test module",
        URI.create("https://example.com/support"),
        new SourceModuleIndex.Artifact("ai.sea.modules", "test", "1.0.0"),
        new SourceModuleIndex.Source(URI.create("https://github.com/test"), "MIT"),
        new SourceModuleIndex.Build(List.of("echo ok"), List.of("echo verify")),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
  }

  @Test
  void createAndGetRequest() {
    var store = new FileSystemLocalArtifactInstallRequestStore(workspace);
    var request =
        new LocalArtifactInstallRequest(
            "req-001",
            Instant.parse("2025-01-01T00:00:00Z"),
            testModule(),
            "/tmp/artifact.jar",
            "sha256:abc",
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            LocalArtifactInstallRequest.Status.PENDING,
            null,
            null);
    var saved = store.create(request);
    assertThat(saved.requestId()).isEqualTo("req-001");
    assertThat(store.get("req-001").requestId()).isEqualTo("req-001");
  }

  @Test
  void recentReturnsSorted() {
    var store = new FileSystemLocalArtifactInstallRequestStore(workspace);
    store.create(
        new LocalArtifactInstallRequest(
            "req-1",
            Instant.parse("2025-01-01T00:00:00Z"),
            testModule(),
            "/tmp/a.jar",
            "sha256:aaa",
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            LocalArtifactInstallRequest.Status.PENDING,
            null,
            null));
    store.create(
        new LocalArtifactInstallRequest(
            "req-2",
            Instant.parse("2025-01-02T00:00:00Z"),
            testModule(),
            "/tmp/b.jar",
            "sha256:bbb",
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            LocalArtifactInstallRequest.Status.ALLOWED,
            null,
            null));
    var recent = store.recent(10);
    assertThat(recent).hasSize(2);
    assertThat(recent.getFirst().requestId()).isEqualTo("req-2");
  }

  @Test
  void recentRejectsInvalidLimit() {
    var store = new FileSystemLocalArtifactInstallRequestStore(workspace);
    assertThatThrownBy(() -> store.recent(0))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("between 1 and 100");
    assertThatThrownBy(() -> store.recent(101))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("between 1 and 100");
  }

  @Test
  void getRejectsInvalidId() {
    var store = new FileSystemLocalArtifactInstallRequestStore(workspace);
    assertThatThrownBy(() -> store.get("bad..id"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("invalid request id");
  }

  @Test
  void getRejectsMissingId() {
    var store = new FileSystemLocalArtifactInstallRequestStore(workspace);
    assertThatThrownBy(() -> store.get("nonexistent"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void saveOverwritesExistingRequest() {
    var store = new FileSystemLocalArtifactInstallRequestStore(workspace);
    var module = testModule();
    var req1 =
        new LocalArtifactInstallRequest(
            "req-ovr",
            Instant.parse("2025-01-01T00:00:00Z"),
            module,
            "/tmp/a.jar",
            "sha256:aaa",
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            LocalArtifactInstallRequest.Status.PENDING,
            null,
            "first");
    store.create(req1);
    var req2 =
        new LocalArtifactInstallRequest(
            "req-ovr",
            Instant.parse("2025-01-02T00:00:00Z"),
            module,
            "/tmp/b.jar",
            "sha256:bbb",
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            LocalArtifactInstallRequest.Status.ALLOWED,
            null,
            "second");
    store.save(req2);
    assertThat(store.get("req-ovr").message()).isEqualTo("second");
  }
}
