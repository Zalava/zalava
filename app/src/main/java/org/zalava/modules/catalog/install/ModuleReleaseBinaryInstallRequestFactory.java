package org.zalava.modules.catalog.install;

import org.zalava.modules.catalog.ModuleReleaseSelection;

/** Preserves the selected immutable release checksum until binary installation starts. */
public final class ModuleReleaseBinaryInstallRequestFactory {

  public BinaryModuleInstallRequest create(
      ModuleReleaseSelection.SelectedRelease release,
      String artifactPath,
      String artifactDigest,
      String repositoryId) {
    if (release == null || release.module() == null) {
      throw new SourceModuleInstallationException("Selected module release is required");
    }
    requireText(artifactPath, "artifact path");
    requireText(artifactDigest, "artifact digest");
    requireText(repositoryId, "repository id");
    if (!release.artifactDigest().equals(artifactDigest)) {
      throw new SourceModuleInstallationException(
          "Resolved artifact digest does not match selected module release");
    }
    return new BinaryModuleInstallRequest(
        release.module(), artifactPath, artifactDigest, repositoryId);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new SourceModuleInstallationException("Module release " + field + " is required");
    }
  }
}
