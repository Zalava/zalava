package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BootBuildImageConfigurationTest {

  @Test
  void dockerImageUsesBuildpackManagedJava25LeydenAotCacheWithDisposablePostgresqlTraining()
      throws IOException {
    String buildFile = Files.readString(Path.of("build.gradle"));

    assertThat(buildFile)
        .contains("tasks.named('bootBuildImage')")
        .contains("imageName = \"ghcr.io/zalava/zalava:${project.version}\"")
        .contains("name = 'zalava-local-build-cache'")
        .contains("name = 'zalava-local-launch-cache'")
        .contains("'BP_JVM_VERSION': '25.*'")
        .contains("'BP_JVM_AOTCACHE_ENABLED': 'true'")
        .contains("providers.gradleProperty('leyden.training.network')")
        .contains("providers.gradleProperty('leyden.training.jdbc-url')")
        .contains("leydenImageIntegrationTest")
        .contains("'AGENT_CHANNELS_TELEGRAM_TOKEN': 'false'")
        .doesNotContain("ZALAVA_POSTGRES_JDBC_URL")
        .doesNotContain("ZALAVA_POSTGRES_DOCKER_NETWORK")
        .doesNotContain("com.google.cloud.tools.jib")
        .doesNotContain("jibDockerBuild")
        .doesNotContain("spring.aot.enabled");
  }
}
