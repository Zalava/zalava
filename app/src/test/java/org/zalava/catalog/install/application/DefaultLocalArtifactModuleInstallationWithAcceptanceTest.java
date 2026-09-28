package org.zalava.catalog.install.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.catalog.install.application.port.out.LocalArtifactInstallRequestStore;
import org.zalava.development.CandidateEvaluation;
import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.InstalledModuleAcceptance;
import org.zalava.development.ModuleDevelopmentContract;
import org.zalava.development.application.DevelopmentCandidateValidationGateway;
import org.zalava.development.application.port.out.InstalledModuleAcceptanceStore;

class DefaultLocalArtifactModuleInstallationWithAcceptanceTest {

  private LocalArtifactInstallRequestStore requests;
  private org.zalava.catalog.install.application.port.in.BinaryModuleInstallation installation;
  private LocalArtifactInspection artifacts;
  private DevelopmentCandidateValidationGateway validationGateway;
  private InstalledModuleAcceptanceStore acceptances;
  private Clock clock;
  private DefaultLocalArtifactModuleInstallation service;

  private static final SourceModuleIndex.Module TEST_MODULE = testModule();
  private static final Instant FIXED = Instant.parse("2025-06-01T12:00:00Z");

  private static SourceModuleIndex.Module testModule() {
    return new SourceModuleIndex.Module(
        "test-module",
        "1.0.0",
        "Test Module",
        "A test",
        URI.create("https://example.com"),
        new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
        new SourceModuleIndex.Source(URI.create("https://github.com/x"), "MIT"),
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of("read")));
  }

  private static LocalArtifactInspection.InspectedArtifact inspected() {
    return new LocalArtifactInspection.InspectedArtifact("/tmp/artifact.jar", "sha256:abc123");
  }

  private static LocalArtifactInstallRequest request(String id) {
    return request(id, LocalArtifactInstallRequest.Status.PENDING);
  }

  private static LocalArtifactInstallRequest request(
      String id, LocalArtifactInstallRequest.Status status) {
    return new LocalArtifactInstallRequest(
        id,
        Instant.parse("2025-01-01T00:00:00Z"),
        TEST_MODULE,
        "/tmp/artifact.jar",
        "sha256:abc123",
        null,
        0,
        CandidateEvaluation.Decision.ACCEPTED,
        status,
        null,
        "Awaiting approval");
  }

  private static LocalArtifactInstallRequest requestWithDev(String id, String devId) {
    return new LocalArtifactInstallRequest(
        id,
        Instant.parse("2025-01-01T00:00:00Z"),
        TEST_MODULE,
        "/tmp/artifact.jar",
        "sha256:abc123",
        devId,
        1,
        CandidateEvaluation.Decision.ACCEPTED_WITH_UNVERIFIED_REQUIREMENTS,
        LocalArtifactInstallRequest.Status.PENDING,
        null,
        "Awaiting approval");
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    requests = mock(LocalArtifactInstallRequestStore.class);
    installation =
        mock(org.zalava.catalog.install.application.port.in.BinaryModuleInstallation.class);
    artifacts = mock(LocalArtifactInspection.class);
    validationGateway =
        mock(org.zalava.development.application.DevelopmentCandidateValidationGateway.class);
    acceptances = mock(InstalledModuleAcceptanceStore.class);
    clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    when(artifacts.inspect("/tmp/artifact.jar")).thenReturn(inspected());
    service =
        new DefaultLocalArtifactModuleInstallation(
            requests, installation, artifacts, validationGateway, acceptances, clock);
  }

  @Test
  void createWithDevelopmentRequestCallsValidationGateway() {
    var evidence =
        new DevelopmentCandidateValidationGateway.Evidence(
            "dev-1", 1, CandidateEvaluation.Decision.ACCEPTED);
    when(validationGateway.requireAccepted(any(), any(), any(), any())).thenReturn(evidence);
    when(acceptances.find("test-module")).thenReturn(Optional.empty());
    when(requests.create(any())).thenAnswer(inv -> inv.getArgument(0));

    var result =
        service.create(TEST_MODULE, "/tmp/artifact.jar", new DevelopmentRequestId("dev-1"));

    assertThat(result.developmentRequestId()).isEqualTo("dev-1");
    assertThat(result.candidateAttemptNumber()).isEqualTo(1);
    assertThat(result.validationDecision()).isEqualTo(CandidateEvaluation.Decision.ACCEPTED);
    verify(validationGateway).requireAccepted(any(), any(), any(), any());
  }

  @Test
  void createWithExistingAcceptanceNoDevRequestSkipsCompatibilityCheck() {
    when(acceptances.find("test-module"))
        .thenReturn(Optional.of(mock(InstalledModuleAcceptance.class)));
    when(requests.create(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.create(TEST_MODULE, "/tmp/artifact.jar", null);

    assertThat(result.validationDecision()).isEqualTo(CandidateEvaluation.Decision.ACCEPTED);
    verify(validationGateway, never()).acceptedCandidate(any(), any());
  }

  @Test
  void allowWithDevelopmentRequestSavesAcceptance() {
    var req = requestWithDev("r1", "dev-1");
    when(requests.get("r1")).thenReturn(req);
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var contract = mock(ModuleDevelopmentContract.class);
    var evaluation = mock(CandidateEvaluation.class);
    var accepted =
        new DevelopmentCandidateValidationGateway.AcceptedCandidate(contract, evaluation);
    when(validationGateway.acceptedCandidate(any(DevelopmentRequestId.class), any()))
        .thenReturn(accepted);

    var result = service.allow("r1");

    assertThat(result.status()).isEqualTo(LocalArtifactInstallRequest.Status.SUCCEEDED);
    verify(acceptances).save(any());
  }

  @Test
  void allowWithDevRequestAndNullSourceHandlesCorrectly() {
    var moduleNoSource =
        new SourceModuleIndex.Module(
            "test-module",
            "1.0.0",
            "Test Module",
            "A test",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
            null,
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    var req =
        new LocalArtifactInstallRequest(
            "r1",
            Instant.parse("2025-01-01T00:00:00Z"),
            moduleNoSource,
            "/tmp/artifact.jar",
            "sha256:abc123",
            "dev-1",
            1,
            CandidateEvaluation.Decision.ACCEPTED_WITH_UNVERIFIED_REQUIREMENTS,
            LocalArtifactInstallRequest.Status.PENDING,
            null,
            "Awaiting approval");
    when(requests.get("r1")).thenReturn(req);
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var contract = mock(ModuleDevelopmentContract.class);
    var evaluation = mock(CandidateEvaluation.class);
    var accepted =
        new DevelopmentCandidateValidationGateway.AcceptedCandidate(contract, evaluation);
    when(validationGateway.acceptedCandidate(any(DevelopmentRequestId.class), any()))
        .thenReturn(accepted);

    var result = service.allow("r1");
    assertThat(result.status()).isEqualTo(LocalArtifactInstallRequest.Status.SUCCEEDED);
    verify(acceptances).save(any());
  }

  @Test
  void allowWithoutDevRequestDoesNotSaveAcceptance() {
    when(requests.get("r1")).thenReturn(request("r1"));
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.allow("r1");
    assertThat(result.status()).isEqualTo(LocalArtifactInstallRequest.Status.SUCCEEDED);
    verify(acceptances, never()).save(any());
  }
}
