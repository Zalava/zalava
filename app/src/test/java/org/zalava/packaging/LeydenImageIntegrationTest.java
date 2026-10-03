package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("leyden-image")
@Testcontainers(disabledWithoutDocker = true)
class LeydenImageIntegrationTest {

  private static final String DATABASE_ALIAS = "leyden-postgres";

  @Test
  void buildsLeydenCacheAgainstDisposablePostgresqlContainer() throws Exception {
    String requestedImageName = System.getProperty("zalava.leyden.image-name");
    String imageName =
        requestedImageName == null ? "zalava-leyden-test:" + UUID.randomUUID() : requestedImageName;
    String databasePassword = "leyden-" + UUID.randomUUID();
    DockerClient dockerClient = DockerClientFactory.instance().client();

    try (Network network = Network.newNetwork();
        PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
                .withDatabaseName("zalava_leyden")
                .withUsername("zalava_leyden")
                .withPassword(databasePassword)
                .withNetwork(network)
                .withNetworkAliases(DATABASE_ALIAS)) {
      postgres.start();
      Path buildLog = Files.createTempFile("zalava-leyden-image-", ".log");
      try {
        Process build =
            new ProcessBuilder(
                    "./gradlew",
                    "--no-daemon",
                    ":app:bootBuildImage",
                    "--imageName",
                    imageName,
                    "-Pleyden.training.network=" + network.getId(),
                    "-Pleyden.training.jdbc-url=jdbc:postgresql://"
                        + DATABASE_ALIAS
                        + ":5432/zalava_leyden",
                    "-Pleyden.training.username=" + postgres.getUsername(),
                    "-Pleyden.training.password=" + databasePassword)
                .directory(Path.of("..").toFile())
                .redirectErrorStream(true)
                .redirectOutput(buildLog.toFile())
                .start();

        assertThat(build.waitFor()).isZero();
      } finally {
        Files.deleteIfExists(buildLog);
      }
    } finally {
      if (requestedImageName == null) {
        removeImage(dockerClient, imageName);
      }
    }
  }

  private void removeImage(DockerClient dockerClient, String imageName) {
    try {
      dockerClient.removeImageCmd(imageName).withForce(true).exec();
    } catch (RuntimeException ignored) {
      // The build can fail before its unique candidate image is created.
    }
  }
}
