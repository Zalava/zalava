package org.zalava.catalog.install;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInspection;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalDevelopmentProjectArtifactLocator;
import org.zalava.catalog.install.application.DefaultLocalDevelopmentProjectInstallation;
import org.zalava.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.catalog.install.application.port.in.LocalDevelopmentProjectInstallation;
import org.zalava.development.DevelopmentRequestId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultLocalDevelopmentProjectInstallationTest {
  @TempDir Path root;

  @Test
  void preparesAcceptedCandidateFromModuleMetadataWithoutReleaseIndexDigest() throws Exception {
    Path project = root.resolve("docker");
    Path jar = project.resolve("build/libs/sea-module-docker-1.2.2-SNAPSHOT.jar");
    Files.createDirectories(jar.getParent());
    Files.writeString(jar, "local development artifact");
    Files.writeString(project.resolve("module-metadata.yaml"), metadata());
    LocalArtifactModuleInstallation installations = mock(LocalArtifactModuleInstallation.class);

    new DefaultLocalDevelopmentProjectInstallation(
            installations,
            new FileSystemLocalDevelopmentProjectArtifactLocator(
                new FileSystemLocalArtifactInspection(java.util.List.of(root))))
        .create(
            new LocalDevelopmentProjectInstallation.Request(
                project.toString(), "sea-module-docker", "1.2.2", "development-42"));

    verify(installations)
        .create(
            any(), eq(jar.toRealPath().toString()), eq(new DevelopmentRequestId("development-42")));
  }

  @Test
  void installsAcceptedCandidateWithoutAnAdditionalApproval() throws Exception {
    Path project = root.resolve("docker");
    Path jar = project.resolve("build/libs/sea-module-docker-1.2.2-SNAPSHOT.jar");
    Files.createDirectories(jar.getParent());
    Files.writeString(jar, "local development artifact");
    Files.writeString(project.resolve("module-metadata.yaml"), metadata());
    LocalArtifactModuleInstallation installations = mock(LocalArtifactModuleInstallation.class);
    org.zalava.catalog.LocalArtifactInstallRequest created =
        mock(org.zalava.catalog.LocalArtifactInstallRequest.class);
    when(created.requestId()).thenReturn("local-request");
    when(installations.create(any(), any(), any())).thenReturn(created);

    new DefaultLocalDevelopmentProjectInstallation(
            installations,
            new FileSystemLocalDevelopmentProjectArtifactLocator(
                new FileSystemLocalArtifactInspection(java.util.List.of(root))))
        .install(
            new LocalDevelopmentProjectInstallation.Request(
                project.toString(), "sea-module-docker", "1.2.2", "development-42"));

    verify(installations).allow("local-request");
  }

  private static String metadata() {
    return """
        schemaVersion: 1
        modules:
          - moduleId: sea-module-docker
            version: 1.2.2
            displayName: Docker
            description: Local Docker development build
            supportUrl: https://example.test/docker
            artifact:
              groupId: org.example
              artifactId: sea-module-docker
              version: 1.2.2
            source:
              repository: https://github.com/example/docker.git
              license: Apache-2.0
            build: {command: [], verificationCommand: []}
            compatibility: {seaRuntime: ">=1.0.0 <2.0.0"}
            configurationSchema: {type: object}
            factories:
              - factoryId: docker
                providerType: docker
            operations: []
            security: {permissions: []}
        """;
  }
}
