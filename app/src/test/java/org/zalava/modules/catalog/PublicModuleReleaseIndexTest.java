package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.zalava.modules.catalog.install.ModuleArtifactRepository;

class PublicModuleReleaseIndexTest {
  private final ModuleReleaseIndexLoader loader = new ModuleReleaseIndexLoader();

  @Test
  void selectsPinnedPublicAlphaArtifactAndPreservesSourceEvidence() throws Exception {
    ModuleReleaseIndex index = loader.load(index());
    var selected = new ModuleReleaseSelection().select(index, index.moduleId(), "0.1.0-alpha.4");
    assertThat(selected.repository())
        .isInstanceOf(ModuleArtifactRepository.GitHubReleaseAsset.class);
    var repository = (ModuleArtifactRepository.GitHubReleaseAsset) selected.repository();
    assertThat(repository.releaseTag()).isEqualTo("v0.1.0-alpha.4");
    assertThat(repository.assetName()).isEqualTo("zalava-module-time-0.1.0-alpha.4.jar");
    assertThat(selected.module().source().revision()).matches("[0-9a-f]{40}");
    assertThat(selected.artifactDigest())
        .isEqualTo("sha256:" + index.releases().getFirst().artifact().sha256());
  }

  @Test
  void rejectsInvalidTypedMetadataBeforeDownload() throws Exception {
    String document = index();
    for (String invalid :
        new String[] {
          document.replace("github-release-assets", "guess-from-url"),
          document.replace("\"assetName\": \"zalava-module-time-", "\"assetName\": \"wrong-"),
          document.replace(
              "\"releaseTag\": \"v0.1.0-alpha.4\"", "\"releaseTag\": \"v0.1.0-alpha.3\""),
          document.replace(
              "https://github.com/Zalava/zalava-module-time.git",
              "https://github.com/other/module.git"),
          document.replace(indexRevision(document), "main"),
          document.replace(
              "\"repositoryUri\": \"https://github.com/Zalava/zalava-module-time\"",
              "\"repositoryUri\": \"https://example.test/module\""),
          document.replace(
              "\"repositoryUri\": \"https://github.com/Zalava/zalava-module-time\"",
              "\"repositoryUri\": \"https://github.com/Zalava/zalava-module-time?token=x\"")
        }) {
      assertThatThrownBy(() -> loader.load(invalid))
          .isInstanceOf(SourceModuleIndexValidationException.class);
    }
  }

  private String indexRevision(String document) {
    return loader.load(document).releases().getFirst().source().revision();
  }

  private static String index() throws Exception {
    try (var input =
        PublicModuleReleaseIndexTest.class.getResourceAsStream("/public-time-release-index.yaml")) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
