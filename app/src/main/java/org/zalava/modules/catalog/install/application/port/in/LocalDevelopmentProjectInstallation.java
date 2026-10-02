package org.zalava.modules.catalog.install.application.port.in;

import org.zalava.modules.catalog.LocalArtifactInstallRequest;

/** Installs an accepted local development candidate without a second approval phase. */
public interface LocalDevelopmentProjectInstallation {
  LocalArtifactInstallRequest create(Request request);

  default LocalArtifactInstallRequest install(Request request) {
    return create(request);
  }

  record Request(
      String projectDirectory, String moduleId, String version, String developmentRequestId) {}
}
