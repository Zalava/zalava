package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.catalog.ModuleReleaseSelection;
import org.zalava.catalog.SourceModuleIndex;

class ModuleReleaseBinaryInstallRequestFactoryTest {

  private final ModuleReleaseBinaryInstallRequestFactory factory =
      new ModuleReleaseBinaryInstallRequestFactory();

  @Test
  void createsAnInstallRequestOnlyForTheSelectedReleaseDigest() {
    BinaryModuleInstallRequest request =
        factory.create(release(), "/tmp/zalava-module-time-1.0.1.jar", digest(), "github-packages");

    assertThat(request.module().moduleId()).isEqualTo("zalava-module-time");
    assertThat(request.module().version()).isEqualTo("1.0.1");
    assertThat(request.artifactDigest()).isEqualTo(digest());
    assertThat(request.repositoryId()).isEqualTo("github-packages");
  }

  @Test
  void rejectsAnArtifactThatDoesNotMatchTheSelectedRelease() {
    assertThatThrownBy(
            () ->
                factory.create(
                    release(),
                    "/tmp/zalava-module-time-1.0.1.jar",
                    "sha256:" + "b".repeat(64),
                    "github-packages"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Resolved artifact digest does not match selected module release");
  }

  @Test
  void rejectsMissingRepositoryIdentityBeforeInstallation() {
    assertThatThrownBy(
            () -> factory.create(release(), "/tmp/zalava-module-time-1.0.1.jar", digest(), " "))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Module release repository id is required");
  }

  private static ModuleReleaseSelection.SelectedRelease release() {
    SourceModuleIndex.Module module =
        new SourceModuleIndex.Module(
            "zalava-module-time",
            "1.0.1",
            "zalava-module-time",
            "Immutable release v1.0.1",
            URI.create("https://github.com/Zalava/zalava-module-time"),
            new SourceModuleIndex.Artifact("org.zalava.modules", "zalava-module-time", "1.0.1"),
            new SourceModuleIndex.Source(
                URI.create("https://github.com/Zalava/zalava-module-time"), "Apache-2.0"),
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0 <2.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of("time.read")));
    return new ModuleReleaseSelection.SelectedRelease(module, digest());
  }

  private static String digest() {
    return "sha256:" + "a".repeat(64);
  }
}
