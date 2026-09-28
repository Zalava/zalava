package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInstallRequestStore;
import org.zalava.development.CandidateEvaluation;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class FileSystemLocalArtifactInstallRequestStoreTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path workspace;

  @Test
  void readsARecordWrittenBeforeOptionalDevelopmentEvidence() throws Exception {
    FileSystemLocalArtifactInstallRequestStore store =
        new FileSystemLocalArtifactInstallRequestStore(workspace);
    LocalArtifactInstallRequest current =
        new LocalArtifactInstallRequest(
            "local-1",
            Instant.parse("2026-08-14T10:00:00Z"),
            module(),
            "/trusted/module.jar",
            "sha256:" + "a".repeat(64),
            null,
            0,
            CandidateEvaluation.Decision.ACCEPTED,
            LocalArtifactInstallRequest.Status.PENDING,
            null,
            "Awaiting approval");
    ObjectNode legacy = JSON.valueToTree(current);
    legacy.remove("developmentRequestId");
    legacy.remove("candidateAttemptNumber");
    legacy.remove("validationDecision");
    Files.writeString(
        workspace.resolve("source-module-installation/local-requests/local-1.json"),
        JSON.writeValueAsString(legacy));

    LocalArtifactInstallRequest restored = store.recent(20).getFirst();

    assertThat(restored.requestId()).isEqualTo("local-1");
    assertThat(restored.developmentRequestId()).isNull();
    assertThat(restored.candidateAttemptNumber()).isZero();
    assertThat(restored.validationDecision()).isEqualTo(CandidateEvaluation.Decision.ACCEPTED);
  }

  private static SourceModuleIndex.Module module() {
    return new SourceModuleIndex.Module(
        "sea-module-example",
        "1.0.0",
        "Example",
        "Example module",
        URI.create("https://example.test/support"),
        new SourceModuleIndex.Artifact("org.example", "sea-module-example", "1.0.0"),
        new SourceModuleIndex.Source(
            URI.create("https://github.com/example/sea-module-example"), "Apache-2.0"),
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
  }
}
