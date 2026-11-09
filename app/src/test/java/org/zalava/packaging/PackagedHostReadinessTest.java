package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackagedHostReadinessTest {
  @Test
  void ignoresBoundOrFailedPortsUntilTheApplicationHasStarted() {
    String failed =
        "Tomcat initialized with port 44425 (http)\n"
            + "Web server failed to start. Port 44425 was already in use.\n";
    assertThat(ReleasedModuleDistributableAcceptanceTest.startedApplicationPort(failed)).isEmpty();
    assertThat(ReleasedModuleDistributableAcceptanceTest.isPortCollision(failed, 44425)).isTrue();
    assertThat(ReleasedModuleDistributableAcceptanceTest.isPortCollision(failed, 12345)).isFalse();
    assertThat(
            ReleasedModuleDistributableAcceptanceTest.startedApplicationPort(
                "Tomcat started on port 12345 (http) with context path '/'\n"))
        .isEmpty();
  }

  @Test
  void selectsApplicationPortRatherThanTheLaterManagementPort() {
    String ready =
        "Tomcat started on port 12345 (http) with context path '/'\n"
            + "Tomcat started on port 23456 (http) with context path '/'\n"
            + "Started ZalavaApplication in 3.2 seconds\n";
    assertThat(ReleasedModuleDistributableAcceptanceTest.startedApplicationPort(ready))
        .hasValue(12345);
  }
}
