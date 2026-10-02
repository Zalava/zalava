package org.zalava.modules.catalog.install.application.port.in;

import org.zalava.modules.catalog.install.BinaryModuleInstallRequest;

public interface BinaryModuleInstallation {

  InstalledBinaryModule install(BinaryModuleInstallRequest request);

  record InstalledBinaryModule(
      String moduleId,
      String version,
      String repositoryId,
      String artifactPath,
      String artifactDigest,
      String registryPath) {}
}
