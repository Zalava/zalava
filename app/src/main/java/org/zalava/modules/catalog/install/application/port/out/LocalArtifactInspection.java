package org.zalava.modules.catalog.install.application.port.out;

public interface LocalArtifactInspection {

  InspectedArtifact inspect(String artifactPath);

  record InspectedArtifact(String path, String sha256Digest) {}
}
