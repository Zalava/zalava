package org.zalava.catalog.install;

import java.util.List;
import org.zalava.catalog.SourceModuleIndex;

public record BinaryModuleInstallRequest(
    SourceModuleIndex.Module module,
    String artifactPath,
    String artifactDigest,
    String repositoryId,
    boolean artifactBundle,
    List<RuntimeArtifact> runtimeArtifacts) {
  public BinaryModuleInstallRequest {
    runtimeArtifacts = List.copyOf(runtimeArtifacts);
  }

  public BinaryModuleInstallRequest(
      SourceModuleIndex.Module module,
      String artifactPath,
      String artifactDigest,
      String repositoryId) {
    this(module, artifactPath, artifactDigest, repositoryId, false, List.of());
  }

  public BinaryModuleInstallRequest(
      SourceModuleIndex.Module module,
      String artifactPath,
      String artifactDigest,
      String repositoryId,
      List<RuntimeArtifact> runtimeArtifacts) {
    this(module, artifactPath, artifactDigest, repositoryId, false, runtimeArtifacts);
  }

  public record RuntimeArtifact(
      SourceModuleIndex.Artifact artifact, String artifactPath, String artifactDigest) {}
}
