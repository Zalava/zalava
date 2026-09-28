package org.zalava.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import org.zalava.catalog.LocalArtifactModuleMetadataLoader;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.catalog.install.application.port.out.LocalDevelopmentProjectArtifactLocator;

/** Locates the newest module bundle from a trusted, already-built development project. */
public final class FileSystemLocalDevelopmentProjectArtifactLocator
    implements LocalDevelopmentProjectArtifactLocator {
  private final LocalArtifactInspection artifacts;
  private final LocalArtifactModuleMetadataLoader metadata =
      new LocalArtifactModuleMetadataLoader();

  public FileSystemLocalDevelopmentProjectArtifactLocator(LocalArtifactInspection artifacts) {
    this.artifacts = artifacts;
  }

  @Override
  public ResolvedProjectArtifact resolve(String projectDirectory, String moduleId, String version) {
    Path project = Path.of(projectDirectory).toAbsolutePath().normalize();
    if (!Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(project)) {
      throw new SourceModuleInstallationException(
          "Built module project directory must be a regular directory");
    }
    SourceModuleIndex.Module module = module(project, moduleId, version);
    LocalArtifactInspection.InspectedArtifact artifact =
        artifacts.inspect(jar(project, module).toString());
    return new ResolvedProjectArtifact(module, artifact);
  }

  private SourceModuleIndex.Module module(Path project, String moduleId, String version) {
    try {
      SourceModuleIndex index =
          metadata.load(Files.readString(project.resolve("module-metadata.yaml")));
      return index.modules().stream()
          .filter(candidate -> candidate.moduleId().equals(moduleId))
          .filter(candidate -> candidate.version().equals(version))
          .findFirst()
          .orElseThrow(
              () ->
                  new SourceModuleInstallationException(
                      "Local module id/version is not declared in module-metadata.yaml"));
    } catch (IOException exception) {
      throw new SourceModuleInstallationException("Unable to read module-metadata.yaml", exception);
    }
  }

  private Path jar(Path project, SourceModuleIndex.Module module) {
    Path libraries = project.resolve("build/libs");
    try (var paths = Files.list(libraries)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(
              path ->
                  path.getFileName().toString().startsWith(module.artifact().artifactId() + "-"))
          .filter(path -> path.getFileName().toString().endsWith(".jar"))
          .filter(path -> !path.getFileName().toString().contains("-plain"))
          .filter(path -> !path.getFileName().toString().contains("-sources"))
          .filter(path -> !path.getFileName().toString().contains("-javadoc"))
          .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
          .orElseThrow(
              () ->
                  new SourceModuleInstallationException(
                      "Built module project does not contain a module JAR under build/libs"));
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to inspect the built module project libraries", exception);
    }
  }
}
