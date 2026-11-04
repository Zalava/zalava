package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.ObjectMapper;

/** Runs the existing released-fleet journey through Compose with its external DB fixture. */
final class OciComposeFleet {
  private final List<String> command;
  private final Map<String, String> environment;
  private final Path diagnostics;

  OciComposeFleet(String image, Path diagnostics, List<String> dockerArguments) throws Exception {
    this.diagnostics = diagnostics;
    Files.setPosixFilePermissions(diagnostics, PosixFilePermissions.fromString("rwx------"));
    String project = "zalava-fleet-" + UUID.randomUUID();
    Path repository = Path.of("..").toRealPath();
    Map<String, String> runtime = new LinkedHashMap<>();
    for (int index = 0; index < dockerArguments.size() - 1; index++) {
      if (dockerArguments.get(index).equals("--env")) {
        String value = dockerArguments.get(++index);
        int equals = value.indexOf('=');
        runtime.put(value.substring(0, equals), value.substring(equals + 1));
      }
    }
    runtime.put("SPRING_CONFIG_IMPORT", "optional:classpath:application.private.yaml");
    var mapper = new ObjectMapper();
    Path override = diagnostics.resolve(project + ".yaml");
    String user =
        Files.getAttribute(diagnostics, "unix:uid")
            + ":"
            + Files.getAttribute(diagnostics, "unix:gid");
    Files.writeString(
        override,
        "services:\n  zalava:\n"
            + "    depends_on: !reset {}\n    networks: !reset []\n    network_mode: host\n"
            + "    ports: !reset []\n    secrets: !reset []\n    volumes: !override ["
            + mapper.writeValueAsString(diagnostics + ":" + diagnostics)
            + "]\n"
            + "    user: "
            + mapper.writeValueAsString(user)
            + "\n    group_add: [\"1001\"]\n"
            + "    healthcheck: {disable: true}\n    environment: "
            + mapper.writeValueAsString(runtime)
            + "\n");
    environment =
        new LinkedHashMap<>(
            OciComposeAcceptance.fixtureEnvironment(image, diagnostics.resolve("fleet-secrets")));
    environment.put("ZALAVA_EXTERNAL_DATABASE_URL", runtime.get("SPRING_DATASOURCE_URL"));
    environment.put("ZALAVA_EXTERNAL_DATABASE_USER", runtime.get("SPRING_DATASOURCE_USERNAME"));
    command =
        new ArrayList<>(
            List.of(
                "docker",
                "compose",
                "--project-name",
                project,
                "--file",
                repository.resolve("compose.yaml").toString(),
                "--file",
                repository.resolve("compose.external-db.yaml").toString(),
                "--file",
                override.toString()));
  }

  ProcessBuilder processBuilder() {
    List<String> up = new ArrayList<>(command);
    up.addAll(List.of("up", "--no-deps", "--no-build", "--no-log-prefix", "zalava"));
    ProcessBuilder builder = new ProcessBuilder(up);
    builder.environment().putAll(environment);
    return builder;
  }

  void stop() throws Exception {
    List<String> down = new ArrayList<>(command);
    down.addAll(List.of("down", "--volumes", "--remove-orphans", "--timeout", "20"));
    ProcessBuilder builder =
        new ProcessBuilder(down)
            .redirectErrorStream(true)
            .redirectOutput(
                diagnostics.resolve("compose-stop-" + UUID.randomUUID() + ".log").toFile());
    builder.environment().putAll(environment);
    Process process = builder.start();
    assertThat(process.waitFor(45, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).isZero();
  }
}
