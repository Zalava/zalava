package org.zalava.modules.catalog.install.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.zalava.modules.catalog.LocalArtifactInstallRequest;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.BinaryModuleInstallRequest;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.in.BinaryModuleInstallation;
import org.zalava.modules.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInstallRequestStore;
import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.InstalledModuleAcceptance;
import org.zalava.modules.development.application.DevelopmentCandidateValidationGateway;
import org.zalava.modules.development.application.UpdateCompatibility;
import org.zalava.modules.development.application.port.out.InstalledModuleAcceptanceStore;

public final class DefaultLocalArtifactModuleInstallation
    implements LocalArtifactModuleInstallation {

  private final LocalArtifactInstallRequestStore requests;
  private final BinaryModuleInstallation installation;
  private final LocalArtifactInspection artifacts;
  private final DevelopmentCandidateValidationGateway validationGateway;
  private final Clock clock;
  private final InstalledModuleAcceptanceStore acceptances;

  public DefaultLocalArtifactModuleInstallation(
      LocalArtifactInstallRequestStore requests,
      BinaryModuleInstallation installation,
      LocalArtifactInspection artifacts,
      DevelopmentCandidateValidationGateway validationGateway,
      InstalledModuleAcceptanceStore acceptances,
      Clock clock) {
    this.requests = requests;
    this.installation = installation;
    this.artifacts = artifacts;
    this.validationGateway = validationGateway;
    this.acceptances = acceptances;
    this.clock = clock;
  }

  /**
   * Compatibility constructor for callers that have not yet opted into retained update evidence.
   */
  public DefaultLocalArtifactModuleInstallation(
      LocalArtifactInstallRequestStore requests,
      BinaryModuleInstallation installation,
      LocalArtifactInspection artifacts,
      DevelopmentCandidateValidationGateway validationGateway,
      Clock clock) {
    this(requests, installation, artifacts, validationGateway, null, clock);
  }

  @Override
  public synchronized LocalArtifactInstallRequest create(
      SourceModuleIndex.Module module,
      String artifactPath,
      DevelopmentRequestId developmentRequestId) {
    if (module == null) {
      throw new SourceModuleInstallationException("Local artifact module metadata is required");
    }
    LocalArtifactInspection.InspectedArtifact artifact = artifacts.inspect(artifactPath);
    DevelopmentCandidateValidationGateway.Evidence evidence =
        developmentRequestId == null
            ? null
            : validationGateway.requireAccepted(
                developmentRequestId, artifact, module.moduleId(), module.version());
    if (acceptances != null)
      acceptances
          .find(module.moduleId())
          .ifPresent(
              installed -> {
                if (developmentRequestId == null) return;
                DevelopmentCandidateValidationGateway.AcceptedCandidate candidate =
                    validationGateway.acceptedCandidate(developmentRequestId, artifact);
                UpdateCompatibility.Result result =
                    UpdateCompatibility.compare(
                        installed,
                        candidate.contract(),
                        candidate.evaluation(),
                        module.security().permissions());
                if (result.decision() == UpdateCompatibility.Decision.BREAKING
                    || result.decision() == UpdateCompatibility.Decision.INVALID) {
                  throw new SourceModuleInstallationException(
                      "Module update cannot silently replace the active version: "
                          + String.join("; ", result.changes()));
                }
              });
    return requests.create(
        new LocalArtifactInstallRequest(
            UUID.randomUUID().toString(),
            clock.instant(),
            module,
            artifact.path(),
            artifact.sha256Digest(),
            evidence == null ? null : evidence.developmentRequestId(),
            evidence == null ? 0 : evidence.candidateAttemptNumber(),
            evidence == null
                ? org.zalava.modules.development.CandidateEvaluation.Decision.ACCEPTED
                : evidence.decision(),
            LocalArtifactInstallRequest.Status.PENDING,
            null,
            "Awaiting approval"));
  }

  @Override
  public synchronized LocalArtifactInstallRequest get(String requestId) {
    return requests.get(requestId);
  }

  @Override
  public synchronized List<LocalArtifactInstallRequest> recent(int limit) {
    return requests.recent(limit);
  }

  @Override
  public synchronized LocalArtifactInstallRequest allow(String requestId) {
    LocalArtifactInstallRequest request = get(requestId);
    if (request.status() == LocalArtifactInstallRequest.Status.SUCCEEDED) return request;
    requirePending(request);
    try {
      LocalArtifactInspection.InspectedArtifact artifact =
          artifacts.inspect(request.artifactPath());
      String digest = artifact.sha256Digest();
      if (!digest.equals(request.artifactDigest())) {
        throw new SourceModuleInstallationException(
            "Local artifact digest changed after request creation");
      }
      installation.install(
          new BinaryModuleInstallRequest(
              request.module(), artifact.path(), digest, "local-private"));
      if (acceptances != null && request.developmentRequestId() != null) {
        DevelopmentCandidateValidationGateway.AcceptedCandidate accepted =
            validationGateway.acceptedCandidate(
                new DevelopmentRequestId(request.developmentRequestId()), artifact);
        acceptances.save(
            new InstalledModuleAcceptance(
                request.module().moduleId(),
                request.module().version(),
                accepted.contract(),
                accepted.contract(),
                accepted.contract().acceptanceScenarios(),
                accepted.evaluation(),
                request.module().security().permissions(),
                new InstalledModuleAcceptance.SourceMetadata(
                    digest,
                    "local-private",
                    request.module().source() == null
                        ? null
                        : request.module().source().repository().toString()),
                clock.instant()));
      }
      return save(request, LocalArtifactInstallRequest.Status.SUCCEEDED, "Module enabled");
    } catch (RuntimeException ex) {
      save(request, LocalArtifactInstallRequest.Status.FAILED, "Installation failed");
      throw ex;
    }
  }

  @Override
  public synchronized LocalArtifactInstallRequest deny(String requestId) {
    LocalArtifactInstallRequest request = get(requestId);
    if (request.status() == LocalArtifactInstallRequest.Status.DENIED) return request;
    requirePending(request);
    return save(request, LocalArtifactInstallRequest.Status.DENIED, "Installation denied");
  }

  private LocalArtifactInstallRequest save(
      LocalArtifactInstallRequest request,
      LocalArtifactInstallRequest.Status status,
      String message) {
    return requests.save(
        new LocalArtifactInstallRequest(
            request.requestId(),
            request.createdAt(),
            request.module(),
            request.artifactPath(),
            request.artifactDigest(),
            request.developmentRequestId(),
            request.candidateAttemptNumber(),
            request.validationDecision(),
            status,
            clock.instant(),
            message));
  }

  private void requirePending(LocalArtifactInstallRequest request) {
    if (request.status() != LocalArtifactInstallRequest.Status.PENDING) {
      throw new SourceModuleInstallationException("Local artifact request is not pending");
    }
  }
}
