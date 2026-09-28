package org.zalava.catalog.install.application.port.in;

import java.net.URI;
import java.util.List;
import org.zalava.catalog.ModuleReleaseInstallRequest;

public interface ModuleReleaseInstallation {
  ModuleReleaseInstallRequest create(Request request);

  ModuleReleaseInstallRequest get(String requestId);

  List<ModuleReleaseInstallRequest> recent(int limit);

  ModuleReleaseInstallRequest allow(String requestId);

  ModuleReleaseInstallRequest deny(String requestId);

  record Request(
      URI manifestUri,
      String bearerToken,
      String moduleId,
      String version,
      String repositoryId,
      URI repositoryUrl,
      String developmentRequestId) {}
}
