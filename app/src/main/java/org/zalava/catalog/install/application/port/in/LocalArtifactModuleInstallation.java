package org.zalava.catalog.install.application.port.in;

import java.util.List;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.development.DevelopmentRequestId;

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
