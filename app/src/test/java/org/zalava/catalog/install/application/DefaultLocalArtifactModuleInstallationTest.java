package org.zalava.catalog.install.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.catalog.install.application.port.out.LocalArtifactInstallRequestStore;
import org.zalava.development.CandidateEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultLocalArtifactModuleInstallationTest {

  private org.zalava.catalog.install.application.port.out.LocalArtifactInstallRequestStore
      requests;
  private org.zalava.catalog.install.application.port.in.BinaryModuleInstallation installation;
  private LocalArtifactInspection artifacts;
  private org.zalava.development.application.DevelopmentCandidateValidationGateway
      validationGateway;
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

  private static LocalArtifactInstallRequest request(String id) {
    return request(id, LocalArtifactInstallRequest.Status.PENDING);
  }

  private static LocalArtifactInstallRequest requestWithDev(
      String id, String devRequestId, int attempt) {
    return new LocalArtifactInstallRequest(
        id,
        Instant.parse("2025-01-01T00:00:00Z"),
        TEST_MODULE,
        "/tmp/artifact.jar",
        "sha256:abc123",
        devRequestId,
        attempt,
        CandidateEvaluation.Decision.ACCEPTED,
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
    clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    when(artifacts.inspect("/tmp/artifact.jar")).thenReturn(inspected());
    service =
        new DefaultLocalArtifactModuleInstallation(
            requests, installation, artifacts, validationGateway, clock);
  }

  @Test
  void createWithNullModuleThrows() {
    assertThatThrownBy(() -> service.create(null, "/tmp/artifact.jar", null))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("required");
  }

  @Test
  void createWithoutDevelopmentRequestSetsAcceptedDecision() {
    when(requests.create(any())).thenAnswer(inv -> inv.getArgument(0));
    var result = service.create(TEST_MODULE, "/tmp/artifact.jar", null);
    assertThat(result.requestId()).isNotBlank();
    assertThat(result.validationDecision()).isEqualTo(CandidateEvaluation.Decision.ACCEPTED);
    assertThat(result.developmentRequestId()).isNull();
    assertThat(result.candidateAttemptNumber()).isZero();
    assertThat(result.status()).isEqualTo(LocalArtifactInstallRequest.Status.PENDING);
  }

  @Test
  void getDelegatesToStore() {
    var r = request("r1");
    when(requests.get("r1")).thenReturn(r);
    assertThat(service.get("r1")).isSameAs(r);
  }

  @Test
  void recentDelegatesToStore() {
    when(requests.recent(5)).thenReturn(List.of());
    assertThat(service.recent(5)).isEmpty();
  }

  @Test
  void denyOnPendingChangesStatus() {
    when(requests.get("r1")).thenReturn(request("r1"));
    var denied = request("r1", LocalArtifactInstallRequest.Status.DENIED);
    when(requests.save(any())).thenReturn(denied);
    var result = service.deny("r1");
    assertThat(result.status()).isEqualTo(LocalArtifactInstallRequest.Status.DENIED);
  }

  @Test
  void denyOnAlreadyDeniedReturnsSame() {
    when(requests.get("r1")).thenReturn(request("r1", LocalArtifactInstallRequest.Status.DENIED));
    var result = service.deny("r1");
    assertThat(result.status()).isEqualTo(LocalArtifactInstallRequest.Status.DENIED);
  }

  @Test
  void denyOnNonPendingThrows() {
    when(requests.get("r1"))
        .thenReturn(request("r1", LocalArtifactInstallRequest.Status.SUCCEEDED));
    assertThatThrownBy(() -> service.deny("r1"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("not pending");
  }

  @Test
  void allowOnAlreadySucceededReturnsSame() {
    when(requests.get("r1"))
        .thenReturn(request("r1", LocalArtifactInstallRequest.Status.SUCCEEDED));
    var result = service.allow("r1");
    assertThat(result.status()).isEqualTo(LocalArtifactInstallRequest.Status.SUCCEEDED);
  }

  @Test
  void allowOnNonPendingThrows() {
    when(requests.get("r1")).thenReturn(request("r1", LocalArtifactInstallRequest.Status.DENIED));
    assertThatThrownBy(() -> service.allow("r1"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("not pending");
  }

  @Test
  void allowSucceedsWhenDigestMatches() {
    when(requests.get("r1")).thenReturn(request("r1"));
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var result = service.allow("r1");
    assertThat(result.status()).isEqualTo(LocalArtifactInstallRequest.Status.SUCCEEDED);
  }

  @Test
  void allowFailsWhenDigestMismatch() {
    when(requests.get("r1")).thenReturn(request("r1"));
    when(artifacts.inspect("/tmp/artifact.jar"))
        .thenReturn(
            new LocalArtifactInspection.InspectedArtifact("/tmp/artifact.jar", "sha256:wrong"));
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    assertThatThrownBy(() -> service.allow("r1"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("digest changed");
  }

  @Test
  void allowHandlesInstallationFailure() {
    when(requests.get("r1")).thenReturn(request("r1"));
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(installation.install(any()))
        .thenThrow(new SourceModuleInstallationException("install failed"));
    assertThatThrownBy(() -> service.allow("r1"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("install failed");
  }
}
