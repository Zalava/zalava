package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInspection;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInstallRequestStore;
import org.zalava.catalog.install.application.DefaultLocalArtifactModuleInstallation;
import org.zalava.catalog.install.application.port.in.BinaryModuleInstallation;

class LocalArtifactModuleInstallationTest {
  @TempDir Path workspace;

  @Test
  void createsAndApprovesATrustedLocalJar() throws Exception {
    Path artifact = workspace.resolve("build/zalava-module-time-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, "jar");
    CapturingInstallation binary = new CapturingInstallation();
    var useCase =
        new DefaultLocalArtifactModuleInstallation(
            new FileSystemLocalArtifactInstallRequestStore(workspace),
            binary,
            new FileSystemLocalArtifactInspection(List.of(workspace)),
            acceptedGateway(),
            Clock.systemUTC());
    LocalArtifactInstallRequest request =
        useCase.create(
            module(),
            artifact.toString(),
            new org.zalava.development.DevelopmentRequestId("development-request"));
    assertThat(request.status()).isEqualTo(LocalArtifactInstallRequest.Status.PENDING);
    assertThat(request.artifactDigest()).startsWith("sha256:");
    assertThat(useCase.allow(request.requestId()).status())
        .isEqualTo(LocalArtifactInstallRequest.Status.SUCCEEDED);
    assertThat(binary.request.artifactPath()).isEqualTo(artifact.toRealPath().toString());
    assertThat(useCase.allow(request.requestId()).status())
        .isEqualTo(LocalArtifactInstallRequest.Status.SUCCEEDED);
  }

  @Test
  void rejectsArtifactOutsideTrustedRoot() throws Exception {
    Path artifact = Files.createTempFile("outside", ".jar");
    var useCase =
        new DefaultLocalArtifactModuleInstallation(
            new FileSystemLocalArtifactInstallRequestStore(workspace),
            new CapturingInstallation(),
            new FileSystemLocalArtifactInspection(List.of(workspace)),
            acceptedGateway(),
            Clock.systemUTC());
    assertThatThrownBy(
            () ->
                useCase.create(
                    module(),
                    artifact.toString(),
                    new org.zalava.development.DevelopmentRequestId("development-request")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("trusted root");
  }

  private static SourceModuleIndex.Module module() {
    return new SourceModuleIndex.Module(
        "zalava-module-time",
        "1.0.0",
        "Time",
        "Time",
        URI.create("https://example.test"),
        new SourceModuleIndex.Artifact("org.example", "zalava-module-time", "1.0.0"),
        new SourceModuleIndex.Source(URI.create("https://github.com/example/time"), "Apache-2.0"),
        new SourceModuleIndex.Build(List.of("build"), List.of("test")),
        new SourceModuleIndex.Compatibility(">=1"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
  }

  private static org.zalava.development.application.DevelopmentCandidateValidationGateway
      acceptedGateway() {
    Clock clock = Clock.systemUTC();
    return new org.zalava.development.application.DevelopmentCandidateValidationGateway(
        new org.zalava.development.application.port.out.DevelopmentRequestStore() {
          @Override
          public org.zalava.development.ModuleDevelopmentRequest get(
              org.zalava.development.DevelopmentRequestId id) {
            throw new AssertionError();
          }

          @Override
          public org.zalava.development.ModuleDevelopmentRequest save(
              org.zalava.development.ModuleDevelopmentRequest request) {
            throw new AssertionError();
          }
        },
        new org.zalava.development.application.DevelopmentCandidateEvaluator(clock),
        clock) {
      @Override
      public Evidence requireAccepted(
          org.zalava.development.DevelopmentRequestId id,
          org.zalava.catalog.install.application.port.out.LocalArtifactInspection.InspectedArtifact
              artifact,
          String moduleId,
          String version) {
        return new Evidence(
            id.value(), 1, org.zalava.development.CandidateEvaluation.Decision.ACCEPTED);
      }
    };
  }

  private static final class CapturingInstallation implements BinaryModuleInstallation {
    BinaryModuleInstallRequest request;

    public InstalledBinaryModule install(BinaryModuleInstallRequest request) {
      this.request = request;
      return new InstalledBinaryModule(
          request.module().moduleId(),
          request.module().version(),
          request.repositoryId(),
          request.artifactPath(),
          request.artifactDigest(),
          "registry");
    }
  }
}
