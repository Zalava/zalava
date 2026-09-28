package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.catalog.SourceModuleCatalog;
import org.zalava.catalog.SourceModuleCatalogSearch;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.application.DefaultCuratedMavenModuleInstallation;
import org.zalava.catalog.install.application.port.in.BinaryModuleInstallation;
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver;

class CuratedMavenModuleInstallationTest {

  @Test
  void resolvesOnlyTheSelectedCatalogRepositoryThenInstallsTheVerifiedArtifact() {
    CapturingResolver resolver = new CapturingResolver();
    CapturingInstallation installation = new CapturingInstallation();
    var useCase = new DefaultCuratedMavenModuleInstallation(resolver, installation);

    BinaryModuleInstallation.InstalledBinaryModule installed =
        useCase.install(result(), "maven-central");

    assertThat(resolver.request.repositoryUrl())
        .isEqualTo(URI.create("https://repo.maven.apache.org/maven2"));
    assertThat(resolver.request.artifact()).isEqualTo(module().artifact());
    assertThat(installation.request)
        .isEqualTo(
            new BinaryModuleInstallRequest(
                module(),
                "/tmp/sea-module-time.jar",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "maven-central"));
    assertThat(resolver.discarded).isTrue();
    assertThat(installed.moduleId()).isEqualTo("sea-module-time");
  }

  @Test
  void rejectsARepositoryThatWasNotDeclaredByTheCatalogResult() {
    CapturingResolver resolver = new CapturingResolver();
    var useCase = new DefaultCuratedMavenModuleInstallation(resolver, new CapturingInstallation());

    assertThatThrownBy(() -> useCase.install(result(), "untrusted"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("not declared by the catalog result");
    assertThat(resolver.request).isNull();
  }

  private static SourceModuleCatalogSearch.Result result() {
    return new SourceModuleCatalogSearch.Result(
        module(),
        new SourceModuleCatalog.Entry("sea-module-time", "time.yaml", "a".repeat(64), null),
        List.of(
            new SourceModuleCatalog.MavenRepository(
                "maven-central", "https://repo.maven.apache.org/maven2")));
  }

  private static SourceModuleIndex.Module module() {
    return new SourceModuleIndex.Module(
        "sea-module-time",
        "1.0.0",
        "Time",
        "Time tools",
        URI.create("https://example.test/support"),
        new SourceModuleIndex.Artifact("org.zalava.modules", "sea-module-time", "1.0.0"),
        new SourceModuleIndex.Source(
            URI.create("https://github.com/Zalava/zalava-module-time"), "Apache-2.0"),
        new SourceModuleIndex.Build(List.of("./gradlew", "build"), List.of("./gradlew", "test")),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
  }

  private static final class CapturingResolver implements CuratedMavenArtifactResolver {
    private Request request;
    private boolean discarded;

    @Override
    public ResolvedArtifact resolve(Request request) {
      this.request = request;
      return new ResolvedArtifact(
          "/tmp/sea-module-time.jar",
          "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Override
    public void discard(ResolvedArtifact artifact) {
      discarded = true;
    }
  }

  private static final class CapturingInstallation implements BinaryModuleInstallation {
    private BinaryModuleInstallRequest request;

    @Override
    public InstalledBinaryModule install(BinaryModuleInstallRequest request) {
      this.request = request;
      return new InstalledBinaryModule(
          request.module().moduleId(),
          request.module().version(),
          request.repositoryId(),
          request.artifactPath(),
          request.artifactDigest(),
          "/tmp/enabled-modules.json");
    }
  }
}
