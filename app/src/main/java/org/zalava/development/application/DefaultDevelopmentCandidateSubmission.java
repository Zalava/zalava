package org.zalava.development.application;

import java.util.Objects;
import org.zalava.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.ModuleDevelopmentRequest;
import org.zalava.development.application.port.in.DevelopmentCandidateSubmission;

public final class DefaultDevelopmentCandidateSubmission implements DevelopmentCandidateSubmission {
  private final LocalArtifactInspection artifacts;
  private final DevelopmentCandidateValidationGateway gateway;

  public DefaultDevelopmentCandidateSubmission(
      LocalArtifactInspection artifacts, DevelopmentCandidateValidationGateway gateway) {
    this.artifacts = Objects.requireNonNull(artifacts);
    this.gateway = Objects.requireNonNull(gateway);
  }

  @Override
  public synchronized ModuleDevelopmentRequest submit(
      DevelopmentRequestId requestId, String artifactPath) {
    return gateway.submit(requestId, artifacts.inspect(artifactPath));
  }
}
