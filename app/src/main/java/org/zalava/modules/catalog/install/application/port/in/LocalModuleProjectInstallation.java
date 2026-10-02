package org.zalava.modules.catalog.install.application.port.in;

import org.zalava.modules.catalog.LocalArtifactInstallRequest;

/** Prepares a local binary installation from an already-built module project. */
public interface LocalModuleProjectInstallation {
  LocalArtifactInstallRequest create(Request request);

  record Request(
      String projectDirectory, String moduleId, String version, String developmentRequestId) {}
}
