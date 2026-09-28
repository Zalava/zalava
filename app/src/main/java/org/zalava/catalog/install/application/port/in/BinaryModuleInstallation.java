package org.zalava.catalog.install.application.port.in;

import org.zalava.catalog.install.BinaryModuleInstallRequest;

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
