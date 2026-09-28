package org.zalava.catalog;

import java.net.URI;
import java.util.List;

public record ModuleReleaseIndex(int schemaVersion, String moduleId, List<Release> releases) {

  public ModuleReleaseIndex {
    releases = List.copyOf(releases);
  }

  public record Release(
      String version,
      String releaseTag,
      Artifact artifact,
      List<Artifact> runtimeArtifacts,
      boolean artifactBundle,
      Source source,
      Compatibility compatibility,
      Security security) {
    public Release {
      runtimeArtifacts = List.copyOf(runtimeArtifacts);
    }

    public Release(
        String version,
        String releaseTag,
        Artifact artifact,
        Source source,
        Compatibility compatibility,
        Security security) {
      this(version, releaseTag, artifact, List.of(), false, source, compatibility, security);
    }

    public Release(
        String version,
        String releaseTag,
        Artifact artifact,
        List<Artifact> runtimeArtifacts,
        Source source,
        Compatibility compatibility,
        Security security) {
      this(version, releaseTag, artifact, runtimeArtifacts, false, source, compatibility, security);
    }
  }

  public record Artifact(String groupId, String artifactId, String version, String sha256) {}

  public record Source(URI repository, String license) {}

  public record Compatibility(String seaRuntime) {}

  public record Security(List<String> permissions) {

    public Security {
      permissions = List.copyOf(permissions);
    }
  }
}
