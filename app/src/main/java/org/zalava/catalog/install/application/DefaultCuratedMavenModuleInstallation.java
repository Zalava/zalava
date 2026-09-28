package org.zalava.catalog.install.application;

import org.zalava.catalog.SourceModuleCatalog;
import org.zalava.catalog.SourceModuleCatalogSearch;
import org.zalava.catalog.install.BinaryModuleInstallRequest;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.in.BinaryModuleInstallation;
import org.zalava.catalog.install.application.port.in.CuratedMavenModuleInstallation;
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver;

public final class DefaultCuratedMavenModuleInstallation implements CuratedMavenModuleInstallation {

  private final CuratedMavenArtifactResolver artifacts;
  private final BinaryModuleInstallation installation;

  public DefaultCuratedMavenModuleInstallation(
      CuratedMavenArtifactResolver artifacts, BinaryModuleInstallation installation) {
    this.artifacts = artifacts;
    this.installation = installation;
  }

  @Override
  public BinaryModuleInstallation.InstalledBinaryModule install(
      SourceModuleCatalogSearch.Result result, String repositoryId) {
    if (result == null || result.module() == null) {
      throw new SourceModuleInstallationException("Catalog module result is required");
    }
    SourceModuleCatalog.MavenRepository repository =
        result.repositories().stream()
            .filter(candidate -> candidate.repositoryId().equals(repositoryId))
            .findFirst()
            .orElseThrow(
                () ->
                    new SourceModuleInstallationException(
                        "Binary repository is not declared by the catalog result: "
                            + repositoryId));
    CuratedMavenArtifactResolver.ResolvedArtifact resolved =
        artifacts.resolve(
            new CuratedMavenArtifactResolver.Request(
                repository.repositoryId(),
                java.net.URI.create(repository.url()),
                result.module().artifact()));
    try {
      return installation.install(
          new BinaryModuleInstallRequest(
              result.module(),
              resolved.path(),
              resolved.sha256Digest(),
              repository.repositoryId()));
    } finally {
      artifacts.discard(resolved);
    }
  }
}
