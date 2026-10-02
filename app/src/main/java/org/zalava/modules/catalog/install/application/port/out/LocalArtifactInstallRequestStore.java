package org.zalava.modules.catalog.install.application.port.out;

import java.util.List;
import org.zalava.modules.catalog.LocalArtifactInstallRequest;

public interface LocalArtifactInstallRequestStore {
  LocalArtifactInstallRequest create(LocalArtifactInstallRequest request);

  LocalArtifactInstallRequest get(String requestId);

  List<LocalArtifactInstallRequest> recent(int limit);

  LocalArtifactInstallRequest save(LocalArtifactInstallRequest request);
}
