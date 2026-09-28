package org.zalava.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.junit.jupiter.api.Test;

class ModuleReleaseSelectionTest {

  private final ModuleReleaseSelection selection = new ModuleReleaseSelection();

  @Test
  void selectsOneImmutableReleaseAsBinaryInstallationMetadata() {
    ModuleReleaseSelection.SelectedRelease selected =
        selection.select(index(), "sea-module-time", "1.0.1");
    SourceModuleIndex.Module module = selected.module();

    assertThat(module.moduleId()).isEqualTo("sea-module-time");
    assertThat(module.version()).isEqualTo("1.0.1");
    assertThat(module.artifact())
        .isEqualTo(
            new SourceModuleIndex.Artifact("org.zalava.modules", "sea-module-time", "1.0.1"));
    assertThat(module.compatibility().seaRuntime()).isEqualTo(">=1.0.0 <2.0.0");
    assertThat(module.security().permissions()).containsExactly("time.read");
    assertThat(selected.artifactDigest()).isEqualTo("sha256:" + "a".repeat(64));
  }

  @Test
  void rejectsAReleaseIndexForAnotherModule() {
    assertThatThrownBy(() -> selection.select(index(), "sea-module-shopping-list", "1.0.1"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Module release index does not match module id: sea-module-shopping-list");
  }

  @Test
  void rejectsAnUnavailableVersionWithoutInstallingAnything() {
    assertThatThrownBy(() -> selection.select(index(), "sea-module-time", "1.0.2"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Module release version is not available: sea-module-time@1.0.2");
  }

  private static ModuleReleaseIndex index() {
    return new ModuleReleaseIndex(
        1,
        "sea-module-time",
        List.of(
            new ModuleReleaseIndex.Release(
                "1.0.1",
                "v1.0.1",
                new ModuleReleaseIndex.Artifact(
                    "org.zalava.modules", "sea-module-time", "1.0.1", "a".repeat(64)),
                new ModuleReleaseIndex.Source(
                    URI.create("https://github.com/Zalava/zalava-module-time.git"), "Apache-2.0"),
                new ModuleReleaseIndex.Compatibility(">=1.0.0 <2.0.0"),
                new ModuleReleaseIndex.Security(List.of("time.read")))));
  }
}
