package org.zalava.catalog.install.application.port.out;

import java.util.List;

public interface ModuleEnablement {

  EnablementResult enable(EnabledModule module);

  /**
   * Removes an enabled module from the persisted enablement registry so it is not loaded after the
   * next restart. Disabling an absent module is not an error; the result reports whether anything
   * changed.
   */
  DisableResult disable(String moduleId);

  record EnabledModule(
      String moduleId,
      String version,
      String artifactPath,
      String artifactDigest,
      String seaRuntimeCompatibility,
      String sourceRepository,
      String sourceLicense,
      String binaryRepositoryId,
      List<String> declaredPermissions,
      List<RuntimeArtifact> runtimeArtifacts) {

    public EnabledModule(
        String moduleId,
        String version,
        String artifactPath,
        String artifactDigest,
        String seaRuntimeCompatibility) {
      this(
          moduleId,
          version,
          artifactPath,
          artifactDigest,
          seaRuntimeCompatibility,
          null,
          null,
          null,
          List.of(),
          List.of());
    }

    public EnabledModule {
      declaredPermissions =
          declaredPermissions == null ? List.of() : List.copyOf(declaredPermissions);
      runtimeArtifacts = runtimeArtifacts == null ? List.of() : List.copyOf(runtimeArtifacts);
    }

    public EnabledModule(
        String moduleId,
        String version,
        String artifactPath,
        String artifactDigest,
        String seaRuntimeCompatibility,
        String sourceRepository,
        String sourceLicense,
        String binaryRepositoryId,
        List<String> declaredPermissions) {
      this(
          moduleId,
          version,
          artifactPath,
          artifactDigest,
          seaRuntimeCompatibility,
          sourceRepository,
          sourceLicense,
          binaryRepositoryId,
          declaredPermissions,
          List.of());
    }
  }

  record RuntimeArtifact(String artifactPath, String artifactDigest) {}

  record EnablementResult(String registryPath) {}

  record DisableResult(String moduleId, boolean changed) {}
}
