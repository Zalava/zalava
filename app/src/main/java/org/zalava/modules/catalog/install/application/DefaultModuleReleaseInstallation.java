package org.zalava.modules.catalog.install.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.zalava.modules.catalog.ModuleReleaseInstallRequest;
import org.zalava.modules.catalog.ModuleReleaseSelection;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.application.port.in.CatalogQueries;
import org.zalava.modules.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.modules.catalog.install.BinaryModuleInstallRequest;
import org.zalava.modules.catalog.install.ModuleArtifactRepository;
import org.zalava.modules.catalog.install.ModuleReleaseBinaryInstallRequestFactory;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.in.BinaryModuleInstallation;
import org.zalava.modules.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.modules.catalog.install.application.port.out.CuratedMavenArtifactResolver;
import org.zalava.modules.catalog.install.application.port.out.ModuleReleaseInstallRequestStore;
import org.zalava.modules.development.CandidateEvaluation;
import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.application.DevelopmentCandidateValidationGateway;

/** Prepares a digest-bound binary release, then installs it only after approval. */
public final class DefaultModuleReleaseInstallation implements ModuleReleaseInstallation {
  private final ModuleReleaseIndexRetrieval manifests;
  private final CatalogQueries catalog;
  private final CuratedMavenArtifactResolver artifacts;
  private final ModuleReleaseBinaryInstallRequestFactory requests;
  private final ModuleReleaseInstallRequestStore store;
  private final BinaryModuleInstallation installation;
  private final DevelopmentCandidateValidationGateway validationGateway;
  private final Clock clock;

  public DefaultModuleReleaseInstallation(
      ModuleReleaseIndexRetrieval manifests,
      CatalogQueries catalog,
      CuratedMavenArtifactResolver artifacts,
      ModuleReleaseBinaryInstallRequestFactory requests,
      ModuleReleaseInstallRequestStore store,
      BinaryModuleInstallation installation,
      DevelopmentCandidateValidationGateway validationGateway,
      Clock clock) {
    this.manifests = manifests;
    this.catalog = catalog;
    this.artifacts = artifacts;
    this.requests = requests;
    this.store = store;
    this.installation = installation;
    this.validationGateway = validationGateway;
    this.clock = clock;
  }

  @Override
  public synchronized ModuleReleaseInstallRequest create(Request command) {
    if (command == null)
      throw new SourceModuleInstallationException(
          "Module release installation request is required");
    ModuleReleaseSelection.SelectedRelease release =
        catalog.selectRelease(
            manifests.load(command.manifestUri(), command.bearerToken()),
            command.moduleId(),
            command.version());
    List<CuratedMavenArtifactResolver.ResolvedArtifact> resolved = new ArrayList<>();
    try {
      CuratedMavenArtifactResolver.ResolvedArtifact artifact =
          resolve(
              command,
              release.module().artifact(),
              resolved,
              release.repository(),
              release.artifactDigest());
      List<ModuleReleaseInstallRequest.RuntimeArtifact> runtimeArtifacts =
          release.artifactBundle()
              ? List.of()
              : release.runtimeArtifacts().stream()
                  .map(runtime -> resolveRuntime(command, runtime, resolved))
                  .toList();
      requests.create(release, artifact.path(), artifact.sha256Digest(), command.repositoryId());
      DevelopmentCandidateValidationGateway.Evidence evidence =
          command.developmentRequestId() == null || command.developmentRequestId().isBlank()
              ? null
              : validationGateway.requireAccepted(
                  new DevelopmentRequestId(command.developmentRequestId()),
                  new org.zalava.modules.catalog.install.application.port.out
                      .LocalArtifactInspection.InspectedArtifact(
                      artifact.path(), artifact.sha256Digest()),
                  release.module().moduleId(),
                  release.module().version());
      return store.create(
          new ModuleReleaseInstallRequest(
              UUID.randomUUID().toString(),
              clock.instant(),
              command.manifestUri(),
              release.module(),
              artifact.path(),
              artifact.sha256Digest(),
              runtimeArtifacts,
              release.artifactBundle(),
              command.repositoryId(),
              evidence == null ? null : evidence.developmentRequestId(),
              evidence == null ? 0 : evidence.candidateAttemptNumber(),
              evidence == null ? CandidateEvaluation.Decision.ACCEPTED : evidence.decision(),
              ModuleReleaseInstallRequest.Status.PENDING,
              null,
              "Awaiting approval"));
    } catch (RuntimeException ex) {
      discard(resolved);
      throw ex;
    }
  }

  @Override
  public synchronized ModuleReleaseInstallRequest get(String requestId) {
    return store.get(requestId);
  }

  @Override
  public synchronized List<ModuleReleaseInstallRequest> recent(int limit) {
    return store.recent(limit);
  }

  @Override
  public synchronized ModuleReleaseInstallRequest allow(String requestId) {
    ModuleReleaseInstallRequest request = get(requestId);
    if (request.status() == ModuleReleaseInstallRequest.Status.SUCCEEDED) return request;
    requirePending(request);
    try {
      installation.install(
          new BinaryModuleInstallRequest(
              request.module(),
              request.artifactPath(),
              request.artifactDigest(),
              request.repositoryId(),
              request.artifactBundle(),
              request.runtimeArtifacts().stream()
                  .map(
                      runtime ->
                          new BinaryModuleInstallRequest.RuntimeArtifact(
                              runtime.artifact(), runtime.artifactPath(), runtime.artifactDigest()))
                  .toList()));
      return save(request, ModuleReleaseInstallRequest.Status.SUCCEEDED, "Module enabled");
    } catch (RuntimeException ex) {
      save(request, ModuleReleaseInstallRequest.Status.FAILED, "Installation failed");
      throw ex;
    } finally {
      discard(request);
    }
  }

  @Override
  public synchronized ModuleReleaseInstallRequest deny(String requestId) {
    ModuleReleaseInstallRequest request = get(requestId);
    if (request.status() == ModuleReleaseInstallRequest.Status.DENIED) return request;
    requirePending(request);
    try {
      return save(request, ModuleReleaseInstallRequest.Status.DENIED, "Installation denied");
    } finally {
      discard(request);
    }
  }

  private ModuleReleaseInstallRequest save(
      ModuleReleaseInstallRequest request,
      ModuleReleaseInstallRequest.Status status,
      String message) {
    return store.save(
        new ModuleReleaseInstallRequest(
            request.requestId(),
            request.createdAt(),
            request.manifestUri(),
            request.module(),
            request.artifactPath(),
            request.artifactDigest(),
            request.runtimeArtifacts(),
            request.artifactBundle(),
            request.repositoryId(),
            request.developmentRequestId(),
            request.candidateAttemptNumber(),
            request.validationDecision(),
            status,
            clock.instant(),
            message));
  }

  private CuratedMavenArtifactResolver.ResolvedArtifact resolve(
      Request command,
      SourceModuleIndex.Artifact artifact,
      List<CuratedMavenArtifactResolver.ResolvedArtifact> resolved,
      ModuleArtifactRepository repository,
      String expectedDigest) {
    CuratedMavenArtifactResolver.ResolvedArtifact result =
        artifacts.resolve(
            new CuratedMavenArtifactResolver.Request(
                command.repositoryId(),
                command.repositoryUrl(),
                artifact,
                repository,
                expectedDigest));
    resolved.add(result);
    return result;
  }

  private ModuleReleaseInstallRequest.RuntimeArtifact resolveRuntime(
      Request command,
      ModuleReleaseSelection.RuntimeArtifact runtime,
      List<CuratedMavenArtifactResolver.ResolvedArtifact> resolved) {
    CuratedMavenArtifactResolver.ResolvedArtifact artifact =
        resolve(
            command,
            new SourceModuleIndex.Artifact(
                runtime.artifact().groupId(),
                runtime.artifact().artifactId(),
                runtime.artifact().version()),
            resolved,
            runtime.artifact().repository(),
            runtime.sha256Digest());
    if (!runtime.sha256Digest().equals(artifact.sha256Digest())) {
      throw new SourceModuleInstallationException(
          "Resolved runtime artifact digest does not match release index");
    }
    return new ModuleReleaseInstallRequest.RuntimeArtifact(
        new SourceModuleIndex.Artifact(
            runtime.artifact().groupId(),
            runtime.artifact().artifactId(),
            runtime.artifact().version()),
        artifact.path(),
        artifact.sha256Digest());
  }

  private void discard(ModuleReleaseInstallRequest request) {
    artifacts.discard(
        new CuratedMavenArtifactResolver.ResolvedArtifact(
            request.artifactPath(), request.artifactDigest()));
    request
        .runtimeArtifacts()
        .forEach(
            runtime ->
                artifacts.discard(
                    new CuratedMavenArtifactResolver.ResolvedArtifact(
                        runtime.artifactPath(), runtime.artifactDigest())));
  }

  private void discard(List<CuratedMavenArtifactResolver.ResolvedArtifact> resolved) {
    resolved.forEach(artifacts::discard);
  }

  private static void requirePending(ModuleReleaseInstallRequest request) {
    if (request.status() != ModuleReleaseInstallRequest.Status.PENDING) {
      throw new SourceModuleInstallationException("Module release request is not pending");
    }
  }
}
