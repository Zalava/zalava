package org.zalava.modules.development;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Durable acceptance baseline retained when a validated module becomes active. */
public record InstalledModuleAcceptance(
    String moduleId,
    String installedVersion,
    ModuleDevelopmentContract exposedContract,
    ModuleDevelopmentContract requestedContract,
    List<ModuleDevelopmentContract.AcceptanceScenario> scenarios,
    CandidateEvaluation lastReport,
    List<String> grantedPermissions,
    SourceMetadata source,
    Instant installedAt) {
  public InstalledModuleAcceptance {
    moduleId = required(moduleId, "moduleId");
    installedVersion = required(installedVersion, "installedVersion");
    exposedContract = Objects.requireNonNull(exposedContract, "exposedContract must not be null");
    requestedContract =
        Objects.requireNonNull(requestedContract, "requestedContract must not be null");
    scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
    lastReport = Objects.requireNonNull(lastReport, "lastReport must not be null");
    grantedPermissions = grantedPermissions == null ? List.of() : List.copyOf(grantedPermissions);
    source = Objects.requireNonNull(source, "source must not be null");
    installedAt = Objects.requireNonNull(installedAt, "installedAt must not be null");
  }

  public record SourceMetadata(
      String artifactDigest, String repositoryId, String sourceRepository) {
    public SourceMetadata {
      artifactDigest = required(artifactDigest, "artifactDigest");
      repositoryId = required(repositoryId, "repositoryId");
    }
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
