package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.testcontainers.DockerClientFactory;

/** Disposable bundled Compose deployment/recovery, separate from the external-DB fleet lane. */
final class OciComposeAcceptance {
  private final String image;
  private final Path directory;
  private final String project = "zalava-compose-" + UUID.randomUUID();
  private final Map<String, String> environment;
  private final List<String> command;
  private String app;
  private String database;
  private String baseUrl;
  private int sequence;

  OciComposeAcceptance(String image, Path diagnostics) throws Exception {
    this.image = image;
    directory = Files.createDirectories(diagnostics.resolve("compose"));
    Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    environment = fixtureEnvironment(image, directory.resolve("secrets"));
    Path repository = Path.of("..").toRealPath();
    command =
        List.of(
            "docker",
            "compose",
            "--project-name",
            project,
            "--file",
            repository.resolve("compose.yaml").toString());
  }

  static Map<String, String> fixtureEnvironment(String image, Path secrets) throws Exception {
    Files.createDirectories(secrets);
    Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rwx------"));
    Map<String, String> result = new LinkedHashMap<>();
    result.put("ZALAVA_IMAGE", image);
    result.put("ZALAVA_PORT", "0");
    result.put("ZALAVA_MEMORY", "2g");
    result.put("ZALAVA_CPUS", "2");
    result.put(
        "POSTGRES_IMAGE",
        "postgres:18.4-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15");
    result.put(
        "ZALAVA_JAVA_TOOL_OPTIONS",
        Boolean.parseBoolean(System.getProperty("oci.aot", "true")) ? "-XX:AOTMode=on" : "");
    for (String[] setting :
        new String[][] {
          {"ZALAVA_DB_ADMIN_PASSWORD_FILE", "db-admin-password", "admin-" + UUID.randomUUID()},
          {"ZALAVA_DB_PASSWORD_FILE", "db-app-password", "app-" + UUID.randomUUID()},
          {"ZALAVA_BOOTSTRAP_LOGIN_FILE", "bootstrap-login", "compose-administrator"},
          {
            "ZALAVA_BOOTSTRAP_PASSWORD_FILE",
            "bootstrap-password",
            "fixture-password-" + UUID.randomUUID()
          }
        }) {
      Path file = secrets.resolve(setting[1]);
      if (!Files.exists(file)) Files.writeString(file, setting[2]);
      // Compose file secrets are bind mounts: target uid/gid/mode cannot fix source mode.
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
      result.put(setting[0], file.toAbsolutePath().toString());
    }
    return result;
  }

  void verify() throws Exception {
    try {
      Map<String, String> invalid = new LinkedHashMap<>(environment);
      invalid.put("ZALAVA_IMAGE", "");
      assertThat(execute("invalid-config", invalid, null, "config", "--quiet")).isNotZero();
      run("config", "config", "--quiet");
      up();
      var docker = DockerClientFactory.instance().client();
      var inspect = docker.inspectContainerCmd(app).exec();
      assertThat(inspect.getConfig().getUser()).isEqualTo("1002:1001");
      assertThat(inspect.getImageId()).isEqualTo(docker.inspectImageCmd(image).exec().getId());
      assertThat(inspect.getHostConfig().getMemory()).isEqualTo(2L * 1024 * 1024 * 1024);
      assertThat(inspect.getHostConfig().getNanoCPUs()).isEqualTo(2_000_000_000L);
      assertThat(
              new String(
                  new ProcessBuilder("docker", "port", database)
                      .start()
                      .getInputStream()
                      .readAllBytes(),
                  StandardCharsets.UTF_8))
          .isBlank();
      String role =
          sql(
              "SELECT rolsuper || ':' || rolcreatedb || ':' || rolcreaterole FROM pg_roles WHERE rolname='zalava'");
      assertThat(role).contains("false:false:false");
      String password =
          Files.readString(Path.of(environment.get("ZALAVA_BOOTSTRAP_PASSWORD_FILE")));
      browser(password, true);

      sql(
          "CREATE TABLE oci_recovery_marker (value text NOT NULL); INSERT INTO oci_recovery_marker VALUES ('"
              + project
              + "')");
      run(
          "recreate-app",
          "up",
          "--detach",
          "--no-build",
          "--no-deps",
          "--force-recreate",
          "--wait",
          "zalava");
      identify();
      assertThat(sql("SELECT value FROM oci_recovery_marker")).contains(project);
      browser(password, false);
      run("stop-db", "stop", "--timeout", "20", "postgres");
      awaitHealth(false);
      run("start-db", "start", "postgres");
      awaitHealth(true);
      browser(password, false);

      run(
          "state-marker",
          "run",
          "--rm",
          "--no-deps",
          "--user",
          "1002:1001",
          "--entrypoint",
          "/bin/sh",
          "state-init",
          "-ec",
          "printf '%s' '" + project + "' > /state/oci-recovery-marker");
      run("stop-app-before-restore", "stop", "--timeout", "20", "zalava");
      Path backup = directory.resolve("database.dump");
      assertThat(
              execute(
                  "backup",
                  environment,
                  backup,
                  "exec",
                  "-T",
                  "postgres",
                  "pg_dump",
                  "--username",
                  "postgres",
                  "--dbname",
                  "zalava",
                  "--format",
                  "custom"))
          .isZero();
      assertThat(Files.size(backup)).isPositive();
      Path stateBackup = directory.resolve("state.tar");
      assertThat(
              execute(
                  "backup-state",
                  environment,
                  stateBackup,
                  "run",
                  "--rm",
                  "--no-deps",
                  "--user",
                  "1002:1001",
                  "--entrypoint",
                  "tar",
                  "state-init",
                  "-C",
                  "/state",
                  "-cf",
                  "-",
                  "."))
          .isZero();
      assertThat(Files.size(stateBackup)).isPositive();
      run(
          "damage-state-marker",
          "run",
          "--rm",
          "--no-deps",
          "--user",
          "1002:1001",
          "--entrypoint",
          "/bin/sh",
          "state-init",
          "-ec",
          "printf '%s' changed > /state/oci-recovery-marker");
      List<String> stateRestore = new ArrayList<>(command);
      stateRestore.addAll(
          List.of(
              "run",
              "--rm",
              "--no-deps",
              "--user",
              "1002:1001",
              "--entrypoint",
              "tar",
              "state-init",
              "-C",
              "/state",
              "-xf",
              "-"));
      Process stateRestored =
          builder(stateRestore)
              .redirectInput(stateBackup.toFile())
              .redirectErrorStream(true)
              .redirectOutput(directory.resolve("restore-state.log").toFile())
              .start();
      assertThat(stateRestored.waitFor(60, TimeUnit.SECONDS)).isTrue();
      assertThat(stateRestored.exitValue()).isZero();
      assertThat(
              output(
                  "state-marker-restored",
                  "run",
                  "--rm",
                  "--no-deps",
                  "--user",
                  "1002:1001",
                  "--entrypoint",
                  "cat",
                  "state-init",
                  "/state/oci-recovery-marker"))
          .contains(project);
      sql("DELETE FROM oci_recovery_marker");
      assertThat(sql("SELECT count(*) FROM oci_recovery_marker")).contains("0");
      List<String> restore = new ArrayList<>(command);
      restore.addAll(
          List.of(
              "exec",
              "-T",
              "postgres",
              "pg_restore",
              "--username",
              "postgres",
              "--dbname",
              "zalava",
              "--clean",
              "--if-exists"));
      ProcessBuilder restoration =
          builder(restore)
              .redirectInput(backup.toFile())
              .redirectErrorStream(true)
              .redirectOutput(directory.resolve("restore.log").toFile());
      Process restored = restoration.start();
      assertThat(restored.waitFor(60, TimeUnit.SECONDS)).isTrue();
      assertThat(restored.exitValue()).isZero();
      run("restart-after-restore", "up", "--detach", "--no-build", "--wait");
      identify();
      assertThat(sql("SELECT value FROM oci_recovery_marker")).contains(project);
      browser(password, false);
      // Recreate DB container too: its named PG18 volume and application account must survive.
      run("recreate-db", "up", "--detach", "--no-build", "--force-recreate", "--wait", "postgres");
      identify();
      awaitHealth(true);
      assertThat(sql("SELECT value FROM oci_recovery_marker")).contains(project);
      browser(password, false);
      run("graceful-stop", "stop", "--timeout", "20", "zalava");
      run("app-logs", "logs", "--no-color", "zalava");
      String logs = Files.readString(directory.resolve("app-logs.log"));
      assertThat(logs).contains("Graceful shutdown complete");
      assertThat(logs).doesNotContain("Unable to map shared spaces", "size has changed");
    } finally {
      execute("failure-diagnostics", environment, null, "logs", "--no-color");
      run("teardown", "down", "--volumes", "--remove-orphans", "--timeout", "20");
      var remaining =
          DockerClientFactory.instance()
              .client()
              .listContainersCmd()
              .withShowAll(true)
              .withLabelFilter(Map.of("com.docker.compose.project", project))
              .exec();
      assertThat(remaining).isEmpty();
      // Backups and secret material are disposable; retain only logs/screenshots/state evidence.
      Files.deleteIfExists(directory.resolve("database.dump"));
      Files.deleteIfExists(directory.resolve("state.tar"));
    }
  }

  private void up() throws Exception {
    run("up", "up", "--detach", "--no-build", "--wait", "--wait-timeout", "120");
    identify();
  }

  private void identify() throws Exception {
    app = output("app-id", "ps", "--quiet", "zalava").trim();
    database = output("db-id", "ps", "--quiet", "postgres").trim();
    assertThat(app).isNotBlank();
    assertThat(database).isNotBlank();
    baseUrl = "http://" + output("app-port", "port", "zalava", "8080").trim();
  }

  private void awaitHealth(boolean healthy) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(100).toNanos();
    while (System.nanoTime() < deadline) {
      Process check =
          new ProcessBuilder("docker", "exec", app, "/workspace/health-check")
              .redirectErrorStream(true)
              .redirectOutput(directory.resolve("readiness.log").toFile())
              .start();
      assertThat(check.waitFor(10, TimeUnit.SECONDS)).isTrue();
      if ((check.exitValue() == 0) == healthy) return;
      Thread.sleep(500);
    }
    throw new AssertionError(
        "Compose readiness did not become " + healthy + "; diagnostics: " + directory);
  }

  private String sql(String statement) throws Exception {
    return output(
        "sql-" + ++sequence,
        "exec",
        "-T",
        "postgres",
        "psql",
        "--username",
        "postgres",
        "--dbname",
        "zalava",
        "--tuples-only",
        "--no-align",
        "--set",
        "ON_ERROR_STOP=1",
        "--command",
        statement);
  }

  private void browser(String password, boolean checkDenial) {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        var context = browser.newContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl + "/login");
      if (checkDenial) {
        page.locator("input[name=username]").fill("compose-administrator");
        page.locator("input[name=password]").fill("wrong-password");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        assertThat(page.url()).contains("/login");
      }
      page.locator("input[name=username]").fill("compose-administrator");
      page.locator("input[name=password]").fill(password);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
      page.waitForURL(url -> !url.contains("/login"));
      assertThat(context.request().get(baseUrl + "/modules").status()).isEqualTo(200);
      page.navigate(baseUrl + "/modules");
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(directory.resolve("modules-" + ++sequence + ".png")));
    }
  }

  private ProcessBuilder builder(List<String> arguments) {
    ProcessBuilder result = new ProcessBuilder(arguments);
    result.environment().putAll(environment);
    return result;
  }

  private int execute(String name, Map<String, String> env, Path binary, String... arguments)
      throws Exception {
    List<String> full = new ArrayList<>(command);
    full.addAll(List.of(arguments));
    ProcessBuilder builder = builder(full);
    builder.environment().putAll(env);
    builder.redirectOutput((binary == null ? directory.resolve(name + ".log") : binary).toFile());
    builder.redirectError(directory.resolve(name + "-stderr.log").toFile());
    Process process = builder.start();
    assertThat(process.waitFor(150, TimeUnit.SECONDS))
        .as("Compose %s: %s", name, directory)
        .isTrue();
    return process.exitValue();
  }

  private void run(String name, String... arguments) throws Exception {
    assertThat(execute(name, environment, null, arguments))
        .as("Compose %s: %s", name, directory)
        .isZero();
  }

  private String output(String name, String... arguments) throws Exception {
    run(name, arguments);
    return Files.readString(directory.resolve(name + ".log"));
  }
}
