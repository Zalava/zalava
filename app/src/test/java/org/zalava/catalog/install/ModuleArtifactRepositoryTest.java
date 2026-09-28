package org.zalava.catalog.install;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ModuleArtifactRepositoryTest {
  @Test
  void acceptsOnlyKnownSafeRepositoryForms() {
    new ModuleArtifactRepository.Maven("zalava-maven", URI.create("https://maven.zalava.org/"));
    new ModuleArtifactRepository.GitHubReleaseAsset(
        "zalava-time",
        URI.create("https://github.com/Zalava/zalava-module-time"),
        "v0.1.0-alpha.1",
        "module.jar");
  }

  @Test
  void rejectsUnsafeOrAmbiguousReleaseAssetInputs() {
    assertThatThrownBy(
            () ->
                new ModuleArtifactRepository.GitHubReleaseAsset(
                    "zalava-time", URI.create("https://example.test/module"), "v1", "module.jar"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ModuleArtifactRepository.Maven(
                    "Zalava", URI.create("http://maven.zalava.org/")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
