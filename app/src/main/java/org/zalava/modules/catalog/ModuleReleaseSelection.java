package org.zalava.modules.catalog;

import java.util.List;
import java.util.Map;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;

/** Selects immutable release metadata before a separate approval-gated installation step. */
public final class ModuleReleaseSelection {

  public SelectedRelease select(ModuleReleaseIndex index, String moduleId, String version) {
    if (index == null) {
      throw new SourceModuleInstallationException("Module release index is required");
    }
    requireText(moduleId, "module id");
    requireText(version, "module version");
    if (!moduleId.equals(index.moduleId())) {
      throw new SourceModuleInstallationException(
          "Module release index does not match module id: " + moduleId);
    }
    ModuleReleaseIndex.Release release =
        index.releases().stream()
            .filter(candidate -> version.equals(candidate.version()))
            .findFirst()
            .orElseThrow(
                () ->
                    new SourceModuleInstallationException(
                        "Module release version is not available: " + moduleId + "@" + version));
    return new SelectedRelease(
        module(index.moduleId(), release),
        "sha256:" + release.artifact().sha256(),
        release.artifactBundle(),
        release.runtimeArtifacts().stream()
            .map(artifact -> new RuntimeArtifact(artifact, "sha256:" + artifact.sha256()))
            .toList());
  }

  private static SourceModuleIndex.Module module(
      String moduleId, ModuleReleaseIndex.Release release) {
    return new SourceModuleIndex.Module(
        moduleId,
        release.version(),
        moduleId,
        "Immutable release " + release.releaseTag(),
        release.source().repository(),
        new SourceModuleIndex.Artifact(
            release.artifact().groupId(),
            release.artifact().artifactId(),
            release.artifact().version()),
        new SourceModuleIndex.Source(release.source().repository(), release.source().license()),
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(release.compatibility().zalavaRuntime()),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(release.security().permissions()));
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new SourceModuleInstallationException("Module release " + field + " is required");
    }
  }

  public record SelectedRelease(
      SourceModuleIndex.Module module,
      String artifactDigest,
      boolean artifactBundle,
      List<RuntimeArtifact> runtimeArtifacts) {
    public SelectedRelease {
      runtimeArtifacts = List.copyOf(runtimeArtifacts);
    }

    public SelectedRelease(SourceModuleIndex.Module module, String artifactDigest) {
      this(module, artifactDigest, false, List.of());
    }
  }

  public record RuntimeArtifact(ModuleReleaseIndex.Artifact artifact, String sha256Digest) {}
}
