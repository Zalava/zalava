package org.zalava.modules.catalog.install.application.port.out;

import java.util.List;
import org.zalava.modules.catalog.ModuleReleaseInstallRequest;

public interface ModuleReleaseInstallRequestStore {
  ModuleReleaseInstallRequest create(ModuleReleaseInstallRequest request);

  ModuleReleaseInstallRequest get(String requestId);

  List<ModuleReleaseInstallRequest> recent(int limit);

  ModuleReleaseInstallRequest save(ModuleReleaseInstallRequest request);
}
