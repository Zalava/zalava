package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.testcontainers.DockerClientFactory;

/** Required probes executed against the same built image as the released-fleet journey. */
final class OciRuntimeAcceptance {
  private final String image;
  private final Path directory;
  private final String java;
  private final Path fixture;

  OciRuntimeAcceptance(String image, Path directory) throws Exception {
    this.image = image;
    this.directory = Files.createDirectories(directory.resolve("runtime"));
    Files.setPosixFilePermissions(this.directory, PosixFilePermissions.fromString("rwx------"));
    this.java =
        "/layers/paketo-buildpacks_bellsoft-liberica/"
            + (Boolean.getBoolean("oci.jlink") ? "JLink" : "jre")
            + "/bin/java";
    fixture = Path.of(System.getProperty("zalava.test.external-module-jar")).toAbsolutePath();
  }

  void verify() throws Exception {
    Path secrets = Files.createDirectories(directory.resolve("secrets"));
    Files.writeString(secrets.resolve("zalava.probe.value"), "mounted-value");
    // No application configuration is loaded by the disposable Spring probe.
    List<String> base =
        List.of(
            "--env",
            "SPRING_CONFIG_NAME=oci-probe",
            "--env",
            "SPRING_CONFIG_IMPORT=configtree:/diagnostics/secrets/",
            "--env",
            "BPL_JVM_THREAD_COUNT=50",
            "--env",
            "BPL_JVM_HEAD_ROOM=15",
            "--env",
            "BPL_JAVA_NMT_ENABLED=false",
            "--env",
            "JAVA_TOOL_OPTIONS=-XX:AOTMode=off");
    Result small = run("resources-small", "2g", "1", true, base, List.of("resources"));
    assertThat(small.exit()).isZero();
    assertThat(small.log())
        .contains(
            "PROBE processors=1",
            "PROBE value=mounted-value",
            "PROBE compact=false",
            "PROBE exitOnOom=true",
            "PROBE dump=false",
            "Calculated JVM Memory Configuration");
    List<String> override = new ArrayList<>(base);
    override.addAll(
        List.of(
            "--env",
            "ZALAVA_PROBE_VALUE=environment-value",
            "--env",
            "JAVA_TOOL_OPTIONS=-XX:AOTMode=off -Dzalava.probe.value=system-value"));
    Result large = run("resources-large", "3g", "2", true, override, List.of("resources"));
    assertThat(large.exit()).isZero();
    assertThat(large.log()).contains("PROBE processors=2", "PROBE value=system-value");
    assertThat(number(large.log(), "heap")).isGreaterThan(number(small.log(), "heap"));
    Result cli =
        run(
            "precedence-cli",
            "2g",
            "1",
            true,
            override,
            List.of("resources", "--zalava.probe.value=command-value"));
    assertThat(cli.exit()).isZero();
    assertThat(cli.log()).contains("PROBE value=command-value");
    List<String> environment = new ArrayList<>(base);
    environment.addAll(List.of("--env", "ZALAVA_PROBE_VALUE=environment-value"));
    Result env = run("precedence-env", "2g", "1", true, environment, List.of("resources"));
    assertThat(env.exit()).isZero();
    assertThat(env.log()).contains("PROBE value=environment-value");

    // Compact headers are version-checked on the actual Java 25 runtime. AOT is
    // disabled for this isolated probe: production caches require matching training flags.
    List<String> diagnostic = new ArrayList<>(base);
    diagnostic.addAll(
        List.of(
            "--env",
            "BPL_JFR_ENABLED=true",
            "--env",
            "BPL_JFR_ARGS=filename=/diagnostics/probe.jfr,maxsize=8m,maxage=1m,dumponexit=true",
            "--env",
            "JAVA_TOOL_OPTIONS=-XX:AOTMode=off -XX:+UseCompactObjectHeaders "
                + "-Xlog:gc*:file=/diagnostics/gc.log:time,level,tags:filecount=2,filesize=1m"));
    Result diagnostics = run("diagnostics", "2g", "1", true, diagnostic, List.of("resources"));
    assertThat(diagnostics.exit()).isZero();
    assertThat(diagnostics.log()).contains("PROBE compact=true", "PROBE dump=false");
    assertThat(Files.size(directory.resolve("probe.jfr"))).isBetween(1L, 16L * 1024 * 1024);
    assertThat(Files.size(directory.resolve("gc.log"))).isPositive();
    Result invalid =
        run(
            "invalid-flag",
            "512m",
            "1",
            false,
            List.of(),
            List.of("-XX:+ZalavaUnsupportedFlag", "resources"));
    assertThat(invalid.exit()).isNotZero();
    assertThat(invalid.log()).contains("Unrecognized VM option");

    Result heapDefault =
        run(
            "heap-private-default",
            "512m",
            "1",
            false,
            List.of(),
            List.of("-Xmx32m", "-XX:+ExitOnOutOfMemoryError", "heap"));
    assertThat(heapDefault.exit()).isEqualTo(3);
    assertThat(heapDefault.oomKilled()).isFalse();
    try (var files = Files.list(directory)) {
      assertThat(files.filter(path -> path.toString().endsWith(".hprof"))).isEmpty();
    }
    Result heapDump =
        run(
            "heap-dump",
            "512m",
            "1",
            false,
            List.of(),
            List.of(
                "-Xmx32m",
                "-XX:+ExitOnOutOfMemoryError",
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:HeapDumpPath=/diagnostics/heap.hprof",
                "heap"));
    assertThat(heapDump.exit()).isEqualTo(3);
    assertThat(heapDump.oomKilled()).isFalse();
    assertThat(heapDump.log()).contains("Java heap space", "Heap dump file created");
    assertThat(Files.size(directory.resolve("heap.hprof"))).isBetween(1L, 64L * 1024 * 1024);
    Files.delete(
        directory.resolve("heap.hprof")); // Never retain sensitive heap contents in reports.
    Result killed =
        run(
            "container-oom",
            "96m",
            "1",
            false,
            List.of(),
            List.of(
                "-Xmx32m", "-XX:MaxDirectMemorySize=1g", "-XX:+ExitOnOutOfMemoryError", "native"));
    assertThat(killed.exit()).isEqualTo(137);
    assertThat(killed.oomKilled()).isTrue();
    // A new process from the identical image starts after both failure types.
    Result recovered = run("after-oom", "2g", "1", true, base, List.of("resources"));
    assertThat(recovered.exit()).isZero();
    assertThat(recovered.log()).contains("PROBE value=mounted-value");
  }

  private long number(String log, String name) {
    var matcher = Pattern.compile("PROBE " + name + "=(\\d+)").matcher(log);
    assertThat(matcher.find()).isTrue();
    return Long.parseLong(matcher.group(1));
  }

  private Result run(
      String name,
      String memory,
      String cpus,
      boolean launcher,
      List<String> environment,
      List<String> arguments)
      throws Exception {
    String container = "zalava-runtime-" + UUID.randomUUID();
    List<String> command =
        new ArrayList<>(
            List.of(
                "docker",
                "run",
                "--name",
                container,
                "--network",
                "none",
                "--memory",
                memory,
                "--memory-swap",
                memory,
                "--cpus",
                cpus,
                "--user",
                Files.getAttribute(directory, "unix:uid")
                    + ":"
                    + Files.getAttribute(directory, "unix:gid"),
                "--group-add",
                "1001",
                "--volume",
                fixture + ":/fixture.jar:ro",
                "--volume",
                directory + ":/diagnostics",
                "--entrypoint",
                launcher ? "/cnb/lifecycle/launcher" : java));
    command.addAll(environment);
    command.add(image);
    if (launcher) command.addAll(List.of("--", "java"));
    List<String> flags =
        arguments.stream()
            .filter(value -> value.startsWith("-XX:") || value.startsWith("-Xmx"))
            .toList();
    command.addAll(flags);
    command.addAll(
        List.of(
            "-cp",
            "/workspace/runner.jar:/workspace/lib/*:/workspace/BOOT-INF/classes:/workspace/BOOT-INF/lib/*:/fixture.jar",
            "org.zalava.fixture.OciRuntimeProbe"));
    arguments.stream().filter(value -> !flags.contains(value)).forEach(command::add);
    Path log = directory.resolve(name + ".log");
    var docker = DockerClientFactory.instance().client();
    try {
      Process process =
          new ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
      assertThat(process.waitFor(90, TimeUnit.SECONDS))
          .as("runtime probe %s: %s", name, log)
          .isTrue();
      var state = docker.inspectContainerCmd(container).exec().getState();
      Files.writeString(
          directory.resolve(name + "-state.txt"),
          "exit=" + state.getExitCodeLong() + "\noomKilled=" + state.getOOMKilled() + "\n");
      return new Result(
          process.exitValue(), Boolean.TRUE.equals(state.getOOMKilled()), Files.readString(log));
    } finally {
      docker.removeContainerCmd(container).withForce(true).withRemoveVolumes(true).exec();
    }
  }

  private record Result(int exit, boolean oomKilled, String log) {}
}
