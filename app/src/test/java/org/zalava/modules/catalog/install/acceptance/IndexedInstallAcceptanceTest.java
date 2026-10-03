package org.zalava.modules.catalog.install.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaModule;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.modules.runtime.LoadedZalavaProvider;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.support.PostgreSqlTestDatabase;
import org.zalava.support.RestartableZalavaApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in real-network acceptance for the indexed module-install pipeline.
 *
 * <p>Runs the production adapters against the private locator catalog, the commit-pinned release
 * index, GitHub Packages artifact download and the Zalava administrator control surface. The
 * control surface is exercised with screen security disabled because the lane owns a disposable
 * workspace and port; administrator authorization is covered separately by {@link
 * IndexedInstallPermissionNegativeTest}. Credentials are supplied by the invoking environment and
 * never persisted.
 */
@Tag("indexed-install")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IndexedInstallAcceptanceTest {

  static final String MODULE_ID = "zalava-module-time";
  static final String VERSION = "1.2.0";
  static final String ARTIFACT_DIGEST =
      "sha256:b31aa253e089d80618255ceddeb754d58e8f426d352bed08734bf0a3bef30365";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private static final Path WORKSPACE = createWorkspace();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("zalava.accounts.security-enabled", () -> "false");
    registry.add("zalava.accounts.bootstrap-login", () -> "indexed-install-admin");
    registry.add("zalava.accounts.bootstrap-password", () -> "IndexedInstallPassword-123");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("zalava.catalog.github.token", IndexedInstallCredentials::githubToken);
    registry.add(
        "zalava.catalog.github-packages.username", IndexedInstallCredentials::packagesUsername);
    registry.add("zalava.catalog.github-packages.token", IndexedInstallCredentials::packagesToken);
    String locator = IndexedInstallCredentials.moduleLocatorUrl();
    if (!locator.isBlank()) registry.add("zalava.catalog.module-locator.url", () -> locator);
    PostgreSqlTestDatabase.register(registry);
  }

  @Test
  @Order(1)
  void tamperedArtifactFailsInstallationAndKeepsTheEnabledRegistryUnchanged() throws Exception {
    IndexedInstallCredentials.requireAvailable();
    refreshAndSelect();
    prepare();
    tamperStagedDownload();

    String response =
        post("/zalava/control/module-release-installations/" + latestRequestId() + "/allow");

    assertThat(response).contains("digest");
    assertThat(latestRequestStatus()).isEqualTo("FAILED");
    assertThat(enabledModule("zalava-module-time")).isNull();
  }

  @Test
  @Order(2)
  void installsApprovesRestartsLoadsAndUsesTheIndexedRelease() throws Exception {
    IndexedInstallCredentials.requireAvailable();

    refreshAndSelect();

    String firstRequest = prepare();
    assertThat(post("/zalava/control/module-release-installations/" + firstRequest + "/deny"))
        .contains("Installation denied");
    assertThat(latestRequestStatus()).isEqualTo("DENIED");
    assertThat(enabledModule(MODULE_ID)).isNull();

    String approvedRequest = prepare();
    assertThat(post("/zalava/control/module-release-installations/" + approvedRequest + "/allow"))
        .contains("Module enabled");
    assertThat(latestRequestStatus()).isEqualTo("SUCCEEDED");

    JsonNode enabled = enabledModule(MODULE_ID);
    assertThat(enabled).isNotNull();
    assertThat(enabled.path("version").stringValue("")).isEqualTo(VERSION);
    assertThat(enabled.path("artifactDigest").stringValue("")).isEqualTo(ARTIFACT_DIGEST);
    assertThat(enabled.path("sourceRepository").stringValue(""))
        .isEqualTo("https://github.com/Zalava/zalava-module-time.git");
    assertThat(enabled.path("sourceLicense").stringValue("")).isEqualTo("Apache-2.0");
    assertThat(enabled.path("zalavaRuntimeCompatibility").stringValue(""))
        .isEqualTo(">=1.0.0 <2.0.0");
    assertThat(enabled.path("binaryRepositoryId").stringValue("")).isEqualTo("github-packages");
    assertThat(enabled.path("declaredPermissions")).isEmpty();
    Path installedJar = Path.of(enabled.path("artifactPath").stringValue(""));
    assertThat(installedJar).exists();

    try (var restarted = RestartableZalavaApplicationContext.start(WORKSPACE)) {
      ZalavaRuntime runtime = restarted.getBean(ZalavaRuntime.class);
      List<ZalavaModule> installedModules =
          runtime.modules().stream()
              .filter(module -> module.descriptor().moduleId().equals(MODULE_ID))
              .toList();
      assertThat(installedModules).hasSize(1);
      assertThat(installedModules.getFirst().descriptor().version()).isEqualTo(VERSION);
      assertThat(moduleClassLoaderUrls(installedModules.getFirst())).contains(installedJar.toUri());

      LoadedZalavaProvider loaded =
          runtime
              .findLoadedProvider("jdk-time")
              .orElseThrow(() -> new AssertionError("not loaded"));
      assertThat(loaded.module().moduleId()).isEqualTo(MODULE_ID);
      assertThat(loaded.provider().listTools())
          .extracting(org.zalava.api.ZalavaToolDescriptor::name)
          .contains("current_time", "convert_time");

      ProviderToolOperations operations = restarted.getBean(ProviderToolOperations.class);
      ProviderToolOperations.ToolInvocationOutcome outcome = invokeCurrentTime(operations);
      assertThat(outcome.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
      assertThat(outcome.result().success()).isTrue();
      assertThat(outcome.result().content()).isInstanceOf(Map.class);
      assertThat(((Map<?, ?>) outcome.result().content()).get("instant")).isNotNull();
    }
  }

  private static ProviderToolOperations.ToolInvocationOutcome invokeCurrentTime(
      ProviderToolOperations operations) {
    try {
      return operations.invoke(
          ProviderToolOperations.ToolInvocationCommand.operator(
              "jdk-time",
              "current_time",
              "{}",
              new InvocationContext("indexed-install-acceptance", false, Map.of())));
    } catch (ProviderToolOperationException exception) {
      throw new AssertionError(
          "Installed "
              + MODULE_ID
              + ":"
              + VERSION
              + " is not usable over the indexed install path: "
              + exception.code()
              + " "
              + exception.getMessage()
              + ". The published artifact still targets the retired Jackson 2 ZalavaProvider SPI;"
              + " publish a module-api 1.3.0+ (Jackson 3) compatible release and re-run this lane.",
          exception);
    }
  }

  private void refreshAndSelect() throws Exception {
    assertThat(post("/zalava/control/module-release-installations/catalog/refresh"))
        .contains("Refresh catalog");
    assertThat(
            post(
                "/zalava/control/module-release-installations/catalog/select",
                Map.of("moduleId", MODULE_ID)))
        .contains(MODULE_ID);
  }

  private String prepare() throws Exception {
    String response =
        post(
            "/zalava/control/module-release-installations",
            Map.of("moduleId", MODULE_ID, "version", VERSION));
    assertThat(response).contains(MODULE_ID);
    return latestRequestId();
  }

  private void tamperStagedDownload() throws IOException {
    Path downloads = WORKSPACE.resolve("source-module-installation/downloads");
    try (var paths = Files.list(downloads)) {
      Path staged =
          paths
              .filter(path -> path.getFileName().toString().endsWith(".jar"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("no staged download to tamper"));
      Files.write(
          staged,
          "tampered".getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING);
    }
  }

  private String post(String path) throws Exception {
    return post(path, Map.of());
  }

  private String post(String path, Map<String, String> form) throws Exception {
    String body =
        form.entrySet().stream()
            .map(
                entry ->
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofMinutes(3))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  private String latestRequestId() throws IOException {
    return newestRequestFile().getFileName().toString().replace(".json", "");
  }

  private String latestRequestStatus() throws IOException {
    return JSON.readTree(newestRequestFile().toFile()).path("status").stringValue("");
  }

  private Path newestRequestFile() throws IOException {
    Path directory = WORKSPACE.resolve("source-module-installation/release-requests");
    try (var paths = Files.list(directory)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
          .orElseThrow(() -> new AssertionError("no release request was persisted"));
    }
  }

  private JsonNode enabledModule(String moduleId) throws IOException {
    Path registry = WORKSPACE.resolve("source-module-installation/enabled-modules.json");
    if (!Files.isRegularFile(registry)) return null;
    for (JsonNode entry : JSON.readTree(registry.toFile())) {
      if (moduleId.equals(entry.path("moduleId").stringValue(""))) return entry;
    }
    return null;
  }

  private static List<URI> moduleClassLoaderUrls(ZalavaModule module) {
    ClassLoader classLoader = module.getClass().getClassLoader();
    assertThat(classLoader).isInstanceOf(URLClassLoader.class);
    return java.util.Arrays.stream(((URLClassLoader) classLoader).getURLs())
        .map(
            url -> {
              try {
                return url.toURI();
              } catch (Exception exception) {
                throw new AssertionError("invalid module classpath URL", exception);
              }
            })
        .toList();
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("zalava-indexed-install-");
      Files.writeString(workspace.resolve("AGENT.md"), "Indexed install acceptance workspace.");
      Files.writeString(workspace.resolve("INFO.md"), "Disposable real-network workspace.");
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
