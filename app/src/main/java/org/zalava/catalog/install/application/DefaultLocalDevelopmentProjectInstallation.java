package org.zalava.catalog.install.application;

import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.catalog.install.application.port.in.LocalDevelopmentProjectInstallation;
import org.zalava.catalog.install.application.port.out.LocalDevelopmentProjectArtifactLocator;
import org.zalava.development.DevelopmentRequestId;

/** Uses an accepted development candidate, never a published-release digest. */
public final class DefaultLocalDevelopmentProjectInstallation
    implements LocalDevelopmentProjectInstallation {
  private final LocalArtifactModuleInstallation installations;
  private final LocalDevelopmentProjectArtifactLocator projects;

  public DefaultLocalDevelopmentProjectInstallation(
      LocalArtifactModuleInstallation installations,
      LocalDevelopmentProjectArtifactLocator projects) {
    this.installations = installations;
    this.projects = projects;
  }

  @Override
  public LocalArtifactInstallRequest create(Request request) {
    LocalDevelopmentProjectArtifactLocator.ResolvedProjectArtifact project =
        projects.resolve(request.projectDirectory(), request.moduleId(), request.version());
    return installations.create(
        project.module(),
        project.artifact().path(),
        new DevelopmentRequestId(request.developmentRequestId()));
  }

  @Override
  public LocalArtifactInstallRequest install(Request request) {
    return installations.allow(create(request).requestId());
  }
}
