package org.zalava.catalog.install.application.port.in;

import org.zalava.catalog.ModuleReleaseInstallRequest;

/**
 * Starts a release installation from a catalog module selection, never caller-provided transport
 * details.
 */
public interface ModuleLocatorInstallation {
  ModuleReleaseInstallRequest create(Request request);

  record Request(String moduleId, String version, String developmentRequestId) {}
}
