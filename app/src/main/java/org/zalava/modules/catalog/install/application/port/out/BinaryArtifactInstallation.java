package org.zalava.modules.catalog.install.application.port.out;

import java.util.List;
import org.zalava.modules.catalog.SourceModuleIndex;

public interface BinaryArtifactInstallation {

  InstalledArtifact install(Install install);

  default InstalledBundle installBundle(Install bundle) {
    throw new UnsupportedOperationException(
        "This binary artifact installation does not support bundles");
  }

  default InstalledBundle install(BundleInstall install) {
    if (install.artifacts().size() != 1) {
      throw new UnsupportedOperationException(
          "This binary artifact installation does not support bundles");
    }
    return new InstalledBundle(List.of(install(install.artifacts().getFirst())));
  }

  default void discard(InstalledBundle bundle) {
    // Legacy and non-filesystem implementations have no managed bundle staging to discard.
  }

  record Install(
      String moduleId,
      SourceModuleIndex.Artifact artifact,
      String sourceArtifact,
      String expectedDigest) {}

  record BundleInstall(List<Install> artifacts) {
    public BundleInstall {
      artifacts = List.copyOf(artifacts);
      if (artifacts.isEmpty()) {
        throw new IllegalArgumentException("Binary artifact bundle must not be empty");
      }
    }
  }

  record InstalledArtifact(String path, String digest) {}

  record InstalledBundle(List<InstalledArtifact> artifacts) {
    public InstalledBundle {
      artifacts = List.copyOf(artifacts);
    }
  }
}
