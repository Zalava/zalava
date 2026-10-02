package org.zalava.modules.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.zalava.modules.catalog.ModuleReleaseIndexLoader;
import org.zalava.modules.catalog.ModuleReleaseSelection;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.catalog.install.application.port.out.LocalModuleProjectReleaseLocator;

/** Filesystem adapter for artifacts produced by a module project, without executing its build. */
public final class FileSystemLocalModuleProjectReleaseLocator
    implements LocalModuleProjectReleaseLocator {
  private final LocalArtifactInspection artifacts;
  private final ModuleReleaseIndexLoader indexes;
  private final ModuleReleaseSelection releases;

  public FileSystemLocalModuleProjectReleaseLocator(LocalArtifactInspection artifacts) {
    this(artifacts, new ModuleReleaseIndexLoader(), new ModuleReleaseSelection());
  }

  FileSystemLocalModuleProjectReleaseLocator(
      LocalArtifactInspection artifacts,
      ModuleReleaseIndexLoader indexes,
      ModuleReleaseSelection releases) {
    this.artifacts = artifacts;
    this.indexes = indexes;
    this.releases = releases;
  }

  @Override
  public ResolvedRelease resolve(String projectDirectory, String moduleId, String version) {
    Path project = Path.of(projectDirectory).toAbsolutePath().normalize();
    if (!Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(project)) {
      throw new SourceModuleInstallationException(
          "Built module project directory must be a regular directory");
    }
    Path indexPath = project.resolve("releases/index.yaml").normalize();
    if (!indexPath.startsWith(project)
        || !Files.isRegularFile(indexPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new SourceModuleInstallationException(
          "Built module project must contain releases/index.yaml");
    }
    try {
      ModuleReleaseSelection.SelectedRelease release =
          releases.select(indexes.load(Files.newBufferedReader(indexPath)), moduleId, version);
      Path artifactPath =
          project
              .resolve(
                  "build/libs/" + release.module().artifact().artifactId() + "-" + version + ".jar")
              .normalize();
      if (!artifactPath.startsWith(project))
        throw new SourceModuleInstallationException("Built module artifact path is invalid");
      LocalArtifactInspection.InspectedArtifact artifact =
          artifacts.inspect(artifactPath.toString());
      if (!release.artifactDigest().equals(artifact.sha256Digest())) {
        throw new SourceModuleInstallationException(
            "Built module artifact digest does not match releases/index.yaml");
      }
      return new ResolvedRelease(release, artifact);
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to read built module release index", exception);
    }
  }
}
