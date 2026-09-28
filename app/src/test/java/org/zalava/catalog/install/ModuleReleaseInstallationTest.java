package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.ModuleReleaseInstallRequest;
import org.zalava.catalog.application.DefaultCatalogQueries;
import org.zalava.catalog.install.application.DefaultModuleReleaseInstallation;
import org.zalava.catalog.install.application.port.in.BinaryModuleInstallation;
import org.zalava.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver;
import org.zalava.catalog.install.application.port.out.ModuleReleaseInstallRequestStore;

class ModuleReleaseInstallationTest {
  private final CapturingResolver resolver = new CapturingResolver(digest());
  private final CapturingInstallation installation = new CapturingInstallation(false);
  private final InMemoryStore store = new InMemoryStore();
  private final ModuleReleaseInstallation useCase =
      new DefaultModuleReleaseInstallation(
          (uri, token) -> index(),
          new DefaultCatalogQueries(),
          resolver,
          new ModuleReleaseBinaryInstallRequestFactory(),
          store,
          installation,
          acceptedGateway(),
          Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC));

  @Test
  void preparesVerifiedReleaseAndInstallsOnlyAfterAllowingIt() {
    ModuleReleaseInstallRequest request = useCase.create(command());

    assertThat(request.status()).isEqualTo(ModuleReleaseInstallRequest.Status.PENDING);
    assertThat(request.manifestUri()).isEqualTo(URI.create("https://example.test/releases.yaml"));
    assertThat(request.repositoryId()).isEqualTo("maven-central");
    assertThat(request.artifactDigest()).isEqualTo(digest());
    assertThat(installation.request).isNull();

    assertThat(useCase.allow(request.requestId()).status())
        .isEqualTo(ModuleReleaseInstallRequest.Status.SUCCEEDED);
    assertThat(installation.request.module().moduleId()).isEqualTo("sea-module-time");
    assertThat(installation.request.artifactDigest()).isEqualTo(digest());
    assertThat(resolver.discarded).hasSize(1);
  }

  @Test
  void discardsPreparedArtifactWhenDenied() {
    ModuleReleaseInstallRequest request = useCase.create(command());

    assertThat(useCase.deny(request.requestId()).status())
        .isEqualTo(ModuleReleaseInstallRequest.Status.DENIED);
    assertThat(installation.request).isNull();
    assertThat(resolver.discarded).hasSize(1);
  }

  @Test
  void preparesAnImmutableReleaseWithoutADevelopmentRequest() {
    ModuleReleaseInstallRequest request =
        useCase.create(
            new ModuleReleaseInstallation.Request(
                URI.create("https://example.test/releases.yaml"),
                null,
                "sea-module-time",
                "1.0.0",
                "maven-central",
                URI.create("https://repo.maven.apache.org/maven2"),
                null));

    assertThat(request.developmentRequestId()).isNull();
    assertThat(request.candidateAttemptNumber()).isZero();
    assertThat(request.validationDecision())
        .isEqualTo(org.zalava.development.CandidateEvaluation.Decision.ACCEPTED);
  }

  @Test
  void rejectsArtifactThatDoesNotMatchTheImmutableManifestDigest() {
    CapturingResolver mismatched = new CapturingResolver("sha256:" + "b".repeat(64));
    ModuleReleaseInstallation mismatchedUseCase =
        new DefaultModuleReleaseInstallation(
            (uri, token) -> index(),
            new DefaultCatalogQueries(),
            mismatched,
            new ModuleReleaseBinaryInstallRequestFactory(),
            new InMemoryStore(),
            installation,
            acceptedGateway(),
            Clock.systemUTC());

    assertThatThrownBy(() -> mismatchedUseCase.create(command()))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("does not match selected module release");
    assertThat(mismatched.discarded).hasSize(1);
  }

  @Test
  void recordsFailureAndDiscardsArtifactWhenInstallationFails() {
    ModuleReleaseInstallation failingUseCase =
        new DefaultModuleReleaseInstallation(
            (uri, token) -> index(),
            new DefaultCatalogQueries(),
            resolver,
            new ModuleReleaseBinaryInstallRequestFactory(),
            store,
            new CapturingInstallation(true),
            acceptedGateway(),
            Clock.systemUTC());
    ModuleReleaseInstallRequest request = failingUseCase.create(command());

    assertThatThrownBy(() -> failingUseCase.allow(request.requestId()))
        .isInstanceOf(SourceModuleInstallationException.class);
    assertThat(failingUseCase.get(request.requestId()).status())
        .isEqualTo(ModuleReleaseInstallRequest.Status.FAILED);
    assertThat(resolver.discarded).hasSize(1);
  }

  @Test
  void resolvesAndInstallsEveryApprovedRuntimeArtifactWithThePrimaryModule() {
    CapturingResolver bundleResolver = new CapturingResolver(digest());
    CapturingInstallation bundleInstallation = new CapturingInstallation(false);
    ModuleReleaseInstallation bundleUseCase =
        new DefaultModuleReleaseInstallation(
            (uri, token) -> bundleIndex(),
            new DefaultCatalogQueries(),
            bundleResolver,
            new ModuleReleaseBinaryInstallRequestFactory(),
            new InMemoryStore(),
            bundleInstallation,
            acceptedGateway(),
            Clock.systemUTC());

    ModuleReleaseInstallRequest request = bundleUseCase.create(command());
    bundleUseCase.allow(request.requestId());

    assertThat(bundleResolver.resolved)
        .extracting(resolved -> resolved.artifact().artifactId())
        .containsExactly("sea-module-time", "runtime-dependency");
    assertThat(bundleInstallation.request.runtimeArtifacts()).hasSize(1);
    assertThat(bundleInstallation.request.runtimeArtifacts().getFirst().artifact().artifactId())
        .isEqualTo("runtime-dependency");
    assertThat(bundleResolver.discarded).hasSize(2);
  }

  private static ModuleReleaseInstallation.Request command() {
    return new ModuleReleaseInstallation.Request(
        URI.create("https://example.test/releases.yaml"),
        "secret",
        "sea-module-time",
        "1.0.0",
        "maven-central",
        URI.create("https://repo.maven.apache.org/maven2"),
        "development-request");
  }

  private static ModuleReleaseIndex index() {
    return new ModuleReleaseIndex(
        1,
        "sea-module-time",
        List.of(
            new ModuleReleaseIndex.Release(
                "1.0.0",
                "v1.0.0",
                new ModuleReleaseIndex.Artifact(
                    "org.zalava.modules", "sea-module-time", "1.0.0", digest().substring(7)),
                new ModuleReleaseIndex.Source(
                    URI.create("https://github.com/Zalava/zalava-module-time"), "Apache-2.0"),
                new ModuleReleaseIndex.Compatibility(">=1.0.0 <2.0.0"),
                new ModuleReleaseIndex.Security(List.of("time.read")))));
  }

  private static ModuleReleaseIndex bundleIndex() {
    return new ModuleReleaseIndex(
        1,
        "sea-module-time",
        List.of(
            new ModuleReleaseIndex.Release(
                "1.0.0",
                "v1.0.0",
                new ModuleReleaseIndex.Artifact(
                    "org.zalava.modules", "sea-module-time", "1.0.0", digest().substring(7)),
                List.of(
                    new ModuleReleaseIndex.Artifact(
                        "org.example", "runtime-dependency", "2.0.0", digest().substring(7))),
                new ModuleReleaseIndex.Source(
                    URI.create("https://github.com/Zalava/zalava-module-time"), "Apache-2.0"),
                new ModuleReleaseIndex.Compatibility(">=1.0.0 <2.0.0"),
                new ModuleReleaseIndex.Security(List.of("time.read")))));
  }

  private static String digest() {
    return "sha256:" + "a".repeat(64);
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

  private static final class CapturingResolver implements CuratedMavenArtifactResolver {
    private final String digest;
    private final List<Request> resolved = new ArrayList<>();
    private final List<ResolvedArtifact> discarded = new ArrayList<>();

    private CapturingResolver(String digest) {
      this.digest = digest;
    }

    @Override
    public ResolvedArtifact resolve(Request request) {
      resolved.add(request);
      return new ResolvedArtifact("/tmp/sea-module-time.jar", digest);
    }

    @Override
    public void discard(ResolvedArtifact artifact) {
      discarded.add(artifact);
    }
  }

  private static final class CapturingInstallation implements BinaryModuleInstallation {
    private final boolean fails;
    private BinaryModuleInstallRequest request;

    private CapturingInstallation(boolean fails) {
      this.fails = fails;
    }

    @Override
    public InstalledBinaryModule install(BinaryModuleInstallRequest request) {
      if (fails) throw new SourceModuleInstallationException("installation failed");
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

  private static final class InMemoryStore implements ModuleReleaseInstallRequestStore {
    private final Map<String, ModuleReleaseInstallRequest> requests = new LinkedHashMap<>();

    @Override
    public ModuleReleaseInstallRequest create(ModuleReleaseInstallRequest request) {
      requests.put(request.requestId(), request);
      return request;
    }

    @Override
    public ModuleReleaseInstallRequest get(String requestId) {
      return requests.get(requestId);
    }

    @Override
    public List<ModuleReleaseInstallRequest> recent(int limit) {
      return List.copyOf(requests.values());
    }

    @Override
    public ModuleReleaseInstallRequest save(ModuleReleaseInstallRequest request) {
      requests.put(request.requestId(), request);
      return request;
    }
  }
}
