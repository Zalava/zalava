package org.zalava.modules.catalog.install.application;

import org.zalava.modules.catalog.LocalArtifactInstallRequest;
import org.zalava.modules.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.modules.catalog.install.application.port.in.LocalModuleProjectInstallation;
import org.zalava.modules.catalog.install.application.port.out.LocalModuleProjectReleaseLocator;
import org.zalava.modules.development.DevelopmentRequestId;

/** Reuses the local-artifact approval lifecycle after locating an already-built project binary. */
public final class DefaultLocalModuleProjectInstallation implements LocalModuleProjectInstallation {
  private final LocalModuleProjectReleaseLocator locator;
  private final LocalArtifactModuleInstallation installation;

  public DefaultLocalModuleProjectInstallation(
      LocalModuleProjectReleaseLocator locator, LocalArtifactModuleInstallation installation) {
    this.locator = locator;
    this.installation = installation;
  }

  @Override
  public LocalArtifactInstallRequest create(Request request) {
    LocalModuleProjectReleaseLocator.ResolvedRelease release =
        locator.resolve(request.projectDirectory(), request.moduleId(), request.version());
    return installation.create(
        release.release().module(),
        release.artifact().path(),
        request.developmentRequestId() == null || request.developmentRequestId().isBlank()
            ? null
            : new DevelopmentRequestId(request.developmentRequestId()));
  }
}
