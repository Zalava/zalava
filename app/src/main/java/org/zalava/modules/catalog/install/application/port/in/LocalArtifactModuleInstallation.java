package org.zalava.modules.catalog.install.application.port.in;

import java.util.List;
import org.zalava.modules.catalog.LocalArtifactInstallRequest;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.development.DevelopmentRequestId;

public interface LocalArtifactModuleInstallation {
  LocalArtifactInstallRequest create(
      SourceModuleIndex.Module module,
      String artifactPath,
      DevelopmentRequestId developmentRequestId);

  LocalArtifactInstallRequest get(String requestId);

  List<LocalArtifactInstallRequest> recent(int limit);

  LocalArtifactInstallRequest allow(String requestId);

  LocalArtifactInstallRequest deny(String requestId);
}
