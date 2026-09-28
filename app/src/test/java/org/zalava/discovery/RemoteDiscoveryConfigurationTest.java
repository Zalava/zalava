package org.zalava.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.zalava.discovery.application.RemoteCandidatePolicy;
import org.zalava.discovery.application.port.in.RemoteCapabilityDiscovery;
import org.zalava.discovery.application.port.out.CapabilityGapEvidenceStore;
import org.zalava.discovery.application.port.out.RemoteModuleCatalog;

class RemoteDiscoveryConfigurationTest {

  @TempDir Path workspace;

  private final RemoteDiscoveryConfiguration configuration = new RemoteDiscoveryConfiguration();

  @Test
  void remoteCatalogIsDisabledUnlessExplicitlyEnabledAndConfigured() {
    assertThat(
            configuration
                .remoteModuleCatalog(false, "https://catalog.example/c.yaml", "", 5, 10)
                .configured())
        .isFalse();
    assertThat(configuration.remoteModuleCatalog(true, "", "", 5, 10).configured()).isFalse();
  }

  @Test
  void remoteCatalogIsConfiguredWhenEnabledWithALocatorUrl() {
    RemoteModuleCatalog catalog =
        configuration.remoteModuleCatalog(true, "https://catalog.example/catalog.yaml", "", 5, 10);

    assertThat(catalog.configured()).isTrue();
  }

  @Test
  void candidatePolicyBlocksConfiguredPermissionsCaseInsensitively() {
    RemoteCandidatePolicy policy = configuration.remoteCandidatePolicy("Shell, network");

    assertThat(
            policy.eligible(
                new RemoteModuleCandidate(
                    "zalava-module-a",
                    "1.0.0",
                    "a".repeat(64),
                    "A",
                    "A",
                    java.util.List.of("NETWORK"))))
        .isFalse();
  }

  @Test
  void wiresTheDiscoveryServiceAndEvidenceStore() throws java.io.IOException {
    CapabilityGapEvidenceStore store =
        configuration.capabilityGapEvidenceStore(new FileSystemResource(workspace));

    RemoteCapabilityDiscovery discovery =
        configuration.remoteCapabilityDiscovery(
            RemoteModuleCatalog.disabled(), new RemoteCandidatePolicy(), store, 10);

    assertThat(discovery.discover("pollen", 0).classification())
        .isEqualTo(CapabilityGapClassification.DISABLED);
  }
}
