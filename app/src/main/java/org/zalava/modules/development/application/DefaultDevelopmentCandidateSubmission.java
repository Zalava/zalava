package org.zalava.modules.development.application;

import java.util.Objects;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.ModuleDevelopmentRequest;
import org.zalava.modules.development.application.port.in.DevelopmentCandidateSubmission;

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
