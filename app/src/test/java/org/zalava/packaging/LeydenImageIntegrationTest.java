package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("leyden-image")
class LeydenImageIntegrationTest {

  private static final String DATABASE_ALIAS = "leyden-postgres";

  @Test
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  void buildsLeydenCacheAgainstDisposablePostgresqlContainer() throws Exception {
    String requestedImageName = System.getProperty("zalava.leyden.image-name");
    String imageName =
        requestedImageName == null ? "zalava-leyden-test:" + UUID.randomUUID() : requestedImageName;
    String cachePrefix = "zalava-oci-" + UUID.randomUUID();
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
      Path diagnostics =
          Files.createDirectories(Path.of("build/reports/oci-image", UUID.randomUUID().toString()))
              .toAbsolutePath();
      Path buildLog = diagnostics.resolve("build.log");
      try {
        Process build =
            new ProcessBuilder(
                    "./gradlew",
                    ":app:bootBuildImage",
                    "--imageName",
                    imageName,
                    "-Poci.cache-prefix=" + cachePrefix,
                    "-Poci.jlink=" + System.getProperty("oci.jlink", "false"),
                    "-Poci.aot=" + System.getProperty("oci.aot", "true"),
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

        assertThat(build.waitFor()).as("Buildpacks build; diagnostics: %s", buildLog).isZero();
        var image = dockerClient.inspectImageCmd(imageName).exec();
        assertThat(image.getConfig().getUser())
            .isNotBlank()
            .doesNotStartWith("0:")
            .doesNotStartWith("root:")
            .isNotEqualTo("0")
            .isNotEqualTo("root");
        assertThat(image.getConfig().getEnv())
            .noneMatch(
                value ->
                    value.startsWith("SPRING_DATASOURCE_") || value.contains(databasePassword));
        if (Boolean.getBoolean("zalava.test.oci.acceptance")) {
          verifyJvm(imageName, diagnostics);
          new OciRuntimeAcceptance(imageName, diagnostics).verify();
          if (Boolean.getBoolean("zalava.test.oci.compose")) {
            new OciComposeAcceptance(imageName, diagnostics).verify();
          }
          var acceptance = new ReleasedModuleDistributableAcceptanceTest();
          acceptance.ociImage = imageName;
          acceptance.ociCompose = Boolean.getBoolean("zalava.test.oci.compose");
          acceptance.uploadsReleasedFleetAndPreservesAuthorizedEffectsAcrossPackagedHostRestarts();
        }
      } finally {
        // Remove only this invocation's caches; retain reports for success/failure.
        for (String suffix : new String[] {"-build-cache", "-launch-cache"}) {
          try {
            dockerClient.removeVolumeCmd(cachePrefix + suffix).exec();
          } catch (NotFoundException absent) {
            /* Build may fail before caches are created. */
          }
        }
      }
    } finally {
      if (requestedImageName == null) {
        removeImage(dockerClient, imageName);
      }
    }
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  void rejectsInvalidJvmBuildOptions() throws Exception {
    Path log = Files.createTempFile("zalava-invalid-image-options-", ".log");
    try {
      Process rejected =
          new ProcessBuilder(
                  "./gradlew", ":app:bootBuildImage", "-Poci.jlink=invalid", "-Poci.aot=true")
              .directory(Path.of("..").toFile())
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
      assertThat(rejected.waitFor()).isNotZero();
      assertThat(Files.readString(log)).contains("oci.aot and oci.jlink must be true or false");
    } finally {
      Files.deleteIfExists(log);
    }
  }

  private void verifyJvm(String image, Path diagnostics) throws Exception {
    Path keys = diagnostics.resolve("probe.p12");
    Process keytool =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/keytool").toString(),
                "-genkeypair",
                "-alias",
                "probe",
                "-keyalg",
                "EC",
                "-dname",
                "CN=localhost",
                "-ext",
                "SAN=dns:localhost,ip:127.0.0.1",
                "-validity",
                "2",
                "-storetype",
                "PKCS12",
                "-keystore",
                keys.toString(),
                "-storepass",
                "fixture",
                "-noprompt")
            .redirectErrorStream(true)
            .redirectOutput(diagnostics.resolve("keytool.log").toFile())
            .start();
    assertThat(keytool.waitFor()).isZero();
    Path fixture = Path.of(System.getProperty("zalava.test.external-module-jar")).toAbsolutePath();
    String jvmBinary =
        "/layers/paketo-buildpacks_bellsoft-liberica/"
            + (Boolean.getBoolean("oci.jlink") ? "JLink" : "jre")
            + "/bin/java";
    Process probe =
        new ProcessBuilder(
                "docker",
                "run",
                "--rm",
                "--network",
                "none",
                "--volume",
                fixture + ":/fixture.jar:ro",
                "--volume",
                keys + ":/probe.p12:ro",
                "--entrypoint",
                jvmBinary,
                image,
                "-cp",
                "/workspace/runner.jar:/workspace/lib/*:/workspace/BOOT-INF/classes:/workspace/BOOT-INF/lib/*:/fixture.jar",
                "org.zalava.fixture.OciJvmProbe",
                "/probe.p12")
            .redirectErrorStream(true)
            .redirectOutput(diagnostics.resolve("jvm-probe.log").toFile())
            .start();
    assertThat(probe.waitFor())
        .as("JVM TLS/crypto/JFR/services; diagnostics: %s", diagnostics)
        .isZero();
  }

  private void removeImage(DockerClient dockerClient, String imageName) {
    try {
      dockerClient.removeImageCmd(imageName).withForce(true).exec();
    } catch (RuntimeException ignored) {
      // The build can fail before its unique candidate image is created.
    }
  }
}
