package org.zalava.catalog.install.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.InvocationContext;
import org.zalava.ModuleConfigurationStatus;
import org.zalava.SeaToolDescriptor;
import org.zalava.catalog.FileSystemModuleConfigurationStore;
import org.zalava.catalog.ModuleConfigurationSnapshot;
import org.zalava.managed.ManagedServiceEngine;
import org.zalava.operation.application.port.in.ProviderToolOperationException;
import org.zalava.operation.application.port.in.ProviderToolOperations;
import org.zalava.runtime.LoadedSeaProvider;
import org.zalava.runtime.ManagedSeaRuntime;
import org.zalava.runtime.SeaRuntime;
import org.zalava.support.PostgreSqlTestDatabase;
import org.zalava.support.RestartableSeaApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in cross-repository compatibility matrix for released external modules.
 *
 * <p>Runs the production indexed-install, approval, restart, configuration, tool and module
 * web-extension paths against a candidate SEA build in a disposable workspace on a random port. The
 * matrix pins each module release by version and SHA-256 and fails when a pinned release, provider,
 * tool, page or expected effect is missing rather than skipping it. Credentials come from the
 * invoking environment and are never persisted.
 */
@Tag("module-compat")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModuleCompatibilityAcceptanceTest {

  private static final String FILESYSTEM_PROVIDER_ID = "filesystem-workspace";
  private static final String SHOPPING_PROVIDER_ID = "shopping-list-household";

  static final PinnedRelease FILESYSTEM =
      new PinnedRelease(
          "sea-module-filesystem",
          "1.2.0",
          "c768dec28ccf145825afd97449a3197f63c62ee86b22a473f63ee4dcde1c782c",
          "c768dec28ccf145825afd97449a3197f63c62ee86b22a473f63ee4dcde1c782c",
          "https://github.com/Zalava/zalava-module-filesystem.git",
          null);
  static final PinnedRelease SHOPPING =
      new PinnedRelease(
          "sea-module-shopping-list",
          "1.2.0",
          "d9a622765a5e2af7d35e3fd9aa66592613d7c5d5b36e5043264ae5729dc3ed78",
          "d9a622765a5e2af7d35e3fd9aa66592613d7c5d5b36e5043264ae5729dc3ed78",
          "https://github.com/Zalava/zalava-module-shopping-list.git",
          "shopping-list");
  static final PinnedRelease DOCKER =
      new PinnedRelease(
          "sea-module-docker",
          "1.2.2",
          "61ad5bd81e1d9c7ba901d8081096ad127d3133af646fcc081e0634bb39bb94e5",
          "f1d0572a6fe3067181eb6ab9b56fb66c2da05332edace37002cc3c868282b1e3",
          "https://github.com/Zalava/zalava-module-docker.git",
          null);
  static final PinnedRelease HOME_ASSISTANT =
      new PinnedRelease(
          "sea-module-home-assistant",
          "1.3.0",
          "0080775f49be88e8e8724b9f4370ff40168f5924a1bc19a5928dcb18ad66548f",
          "0080775f49be88e8e8724b9f4370ff40168f5924a1bc19a5928dcb18ad66548f",
          "https://github.com/Zalava/zalava-module-home-assistant.git",
          null);

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private static final Path WORKSPACE = createWorkspace();
  private static final Path PRIVATE_CONFIGURATION_ROOT = WORKSPACE;
  private static final Path FILESYSTEM_ROOT = WORKSPACE.resolve("filesystem/workspace");
  private static final Path SHOPPING_DATABASE =
      WORKSPACE.resolve("shopping-list/shopping-list.sqlite");

  static {
    System.setProperty("sea.module.shopping-list.sqlite.path", SHOPPING_DATABASE.toString());
  }

  @LocalServerPort private int port;

  @Autowired private FileSystemModuleConfigurationStore configurations;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("sea.module-configuration.root", () -> PRIVATE_CONFIGURATION_ROOT.toString());
    registry.add("sea.accounts.security-enabled", () -> "false");
    registry.add("sea.accounts.bootstrap-login", () -> "module-compat-admin");
    registry.add("sea.accounts.bootstrap-password", () -> "ModuleCompatPassword-123");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("sea.catalog.github.token", IndexedInstallCredentials::githubToken);
    registry.add(
        "sea.catalog.github-packages.username", IndexedInstallCredentials::packagesUsername);
    registry.add("sea.catalog.github-packages.token", IndexedInstallCredentials::packagesToken);
    String locator = IndexedInstallCredentials.moduleLocatorUrl();
    if (!locator.isBlank()) registry.add("sea.catalog.module-locator.url", () -> locator);
    PostgreSqlTestDatabase.register(registry);
  }

  @Test
  @Order(1)
  void installsPinnedFilesystemAndShoppingListReleasesAndConfiguresFilesystem() throws Exception {
    IndexedInstallCredentials.requireAvailable();

    installPinnedRelease(FILESYSTEM);
    installPinnedRelease(SHOPPING);
    installPinnedRelease(HOME_ASSISTANT);
    installPinnedRelease(DOCKER);
    assertThat(enabledModule(FILESYSTEM.moduleId())).isNotNull();
    assertThat(enabledModule(SHOPPING.moduleId())).isNotNull();
    assertThat(enabledModule(DOCKER.moduleId())).isNotNull();
    assertThat(enabledModule(HOME_ASSISTANT.moduleId())).isNotNull();

    Map<String, Object> root =
        Map.of(
            "id",
            "workspace",
            "displayName",
            "Compatibility Workspace",
            "path",
            FILESYSTEM_ROOT.toString(),
            "writable",
            true);
    Map<String, Object> filesystemRoots = Map.of("roots", List.of(root));
    configurations.saveCandidate(
        new ModuleConfigurationSnapshot(
            FILESYSTEM.moduleId(),
            FILESYSTEM.version(),
            "filesystem-" + FILESYSTEM.version(),
            Map.<String, Object>of("filesystem-root", filesystemRoots),
            Map.of()),
        Map.of());
    assertThat(configurations.status(FILESYSTEM.moduleId()))
        .isEqualTo(ModuleConfigurationStatus.RESTART_REQUIRED);
  }

  @Test
  @Order(2)
  void restartedCandidateSeaLoadsPinnedModulesAndProvesToolAndPageParity() throws Exception {
    Map<String, String> restartProperties =
        Map.of("sea.module-configuration.root", PRIVATE_CONFIGURATION_ROOT.toString());
    try (var restarted = RestartableSeaApplicationContext.start(WORKSPACE, restartProperties)) {
      SeaRuntime runtime = restarted.getBean(SeaRuntime.class);
      ProviderToolOperations operations = restarted.getBean(ProviderToolOperations.class);
      ManagedSeaRuntime lifecycle = (ManagedSeaRuntime) runtime;
      assertThat(runtime.modules())
          .extracting(module -> module.descriptor().moduleId())
          .contains(
              FILESYSTEM.moduleId(),
              SHOPPING.moduleId(),
              DOCKER.moduleId(),
              HOME_ASSISTANT.moduleId());
      assertThat(runtime.activeModules())
          .extracting(module -> module.descriptor().moduleId())
          .doesNotContain(
              FILESYSTEM.moduleId(),
              SHOPPING.moduleId(),
              DOCKER.moduleId(),
              HOME_ASSISTANT.moduleId());
      lifecycle.applyCandidate(FILESYSTEM.moduleId());
      lifecycle.start(FILESYSTEM.moduleId());
      lifecycle.start(SHOPPING.moduleId());
      assertThatThrownBy(() -> lifecycle.start(DOCKER.moduleId()))
          .isInstanceOf(IllegalStateException.class);
      assertThat(lifecycle.state(DOCKER.moduleId()).state())
          .isEqualTo(ManagedSeaRuntime.State.SETUP_REQUIRED);
      configurations.saveCandidate(
          new ModuleConfigurationSnapshot(
              DOCKER.moduleId(),
              DOCKER.version(),
              "docker-" + DOCKER.version(),
              Map.of(
                  "services",
                  Map.of("engineEndpoint", "unix:///tmp/sea-module-compat-docker.sock"),
                  "docker-containers",
                  Map.of("engineEndpoint", "unix:///tmp/sea-module-compat-docker.sock")),
              Map.of()),
          Map.of());
      lifecycle.applyCandidate(DOCKER.moduleId());
      lifecycle.start(DOCKER.moduleId());
      assertThat(runtime.findService(ManagedServiceEngine.CONTRACT)).isPresent();

      HttpServer homeAssistant = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      homeAssistant.createContext(
          "/api/",
          exchange -> {
            byte[] body = "{\"message\":\"API running.\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
              output.write(body);
            }
          });
      homeAssistant.start();
      try {
        configurations.saveCandidate(
            new ModuleConfigurationSnapshot(
                HOME_ASSISTANT.moduleId(),
                HOME_ASSISTANT.version(),
                "home-" + HOME_ASSISTANT.version(),
                Map.of(
                    "home-assistant",
                    Map.of(
                        "baseUrl",
                        "http://127.0.0.1:" + homeAssistant.getAddress().getPort(),
                        "tokenRef",
                        "compat-home-token")),
                Map.of("tokenRef", "compat-home-token")),
            Map.of("compat-home-token", "fixture-secret"));
        lifecycle.applyCandidate(HOME_ASSISTANT.moduleId());
        lifecycle.start(HOME_ASSISTANT.moduleId());
        var homeStatus = invoke(operations, "home-assistant", "home_assistant_status", "{}");
        assertThat(homeStatus.status())
            .isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
        assertThat(homeStatus.result().success()).isTrue();
      } finally {
        homeAssistant.stop(0);
      }
      int httpPort = ((WebServerApplicationContext) restarted).getWebServer().getPort();

      assertPinnedModuleLoaded(
          runtime,
          FILESYSTEM,
          FILESYSTEM_PROVIDER_ID,
          List.of("listDirectory", "readFile", "writeFile", "Read", "Write", "Edit"));
      assertPinnedModuleLoaded(
          runtime,
          SHOPPING,
          SHOPPING_PROVIDER_ID,
          List.of("add_item", "list_items", "mark_bought", "remove_item", "purchase_summary"));

      proveFilesystemToolParity(operations);
      proveShoppingListPageAndToolParity(operations, httpPort);

      proveBrokenToolFixtureFailsWithDiagnostics(operations);
      proveMissingModulePageFails(httpPort);
      assertPinned(enabledModule(FILESYSTEM.moduleId()), FILESYSTEM);
      assertPinned(enabledModule(SHOPPING.moduleId()), SHOPPING);
      assertPinned(enabledModule(HOME_ASSISTANT.moduleId()), HOME_ASSISTANT);
      assertPinned(enabledModule(DOCKER.moduleId()), DOCKER);
      assertEnabledRegistryStillPinnedAfterFailures();
    }
  }

  @Test
  @Order(3)
  void missingPinnedReleaseFailsWithDiagnosticWithoutChangingTheRegistry() throws Exception {
    IndexedInstallCredentials.requireAvailable();
    refreshAndSelect(FILESYSTEM.moduleId());
    JsonNode pinnedBefore = enabledModule(FILESYSTEM.moduleId());

    String response =
        post(
            "/sea/control/module-release-installations",
            Map.of("moduleId", FILESYSTEM.moduleId(), "version", "9.9.9"));

    assertThat(response)
        .as(
            "an unavailable pinned release must fail with a diagnostic instead of being silently"
                + " skipped")
        .contains("Choose a module and release version from the refreshed catalog");
    assertThat(enabledModule(FILESYSTEM.moduleId())).isEqualTo(pinnedBefore);
    assertThat(latestRequestStatus()).isEqualTo("SUCCEEDED");
  }

  @Test
  @Order(4)
  void mismatchedPinnedDigestFailsTheCompatibilityGate() throws Exception {
    JsonNode enabled = enabledModule(FILESYSTEM.moduleId());
    assertThat(enabled).isNotNull();

    PinnedRelease tampered = FILESYSTEM.withDigest("0".repeat(64));

    assertThatThrownBy(() -> assertPinned(enabled, tampered))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("installed module JAR SHA-256")
        .hasMessageContaining("0".repeat(64));
  }

  private void installPinnedRelease(PinnedRelease pin) throws Exception {
    refreshAndSelect(pin);
    String requestId = prepare(pin);
    assertThat(post("/sea/control/module-release-installations/" + requestId + "/allow"))
        .contains("Module enabled");
    assertThat(latestRequestStatus()).isEqualTo("SUCCEEDED");
  }

  private void refreshAndSelect(String moduleId) throws Exception {
    assertThat(post("/sea/control/module-release-installations/catalog/refresh"))
        .contains("Refresh catalog");
    assertThat(
            post(
                "/sea/control/module-release-installations/catalog/select",
                Map.of("moduleId", moduleId)))
        .contains(moduleId);
  }

  private void refreshAndSelect(PinnedRelease pin) throws Exception {
    assertThat(post("/sea/control/module-release-installations/catalog/refresh"))
        .contains("Refresh catalog");
    assertThat(
            post(
                "/sea/control/module-release-installations/catalog/select",
                Map.of("moduleId", pin.moduleId())))
        .contains(pin.moduleId(), pin.version());
  }

  private String prepare(PinnedRelease pin) throws Exception {
    String response =
        post(
            "/sea/control/module-release-installations",
            Map.of("moduleId", pin.moduleId(), "version", pin.version()));
    assertThat(response).contains(pin.moduleId(), pin.version());
    assertThat(response)
        .as("release preparation must not be rejected by the control endpoint")
        .doesNotContain("alert-danger");
    JsonNode prepared = JSON.readTree(newestRequestFile().toFile());
    assertThat(prepared.path("module").path("moduleId").asText()).isEqualTo(pin.moduleId());
    assertThat(prepared.path("module").path("version").asText()).isEqualTo(pin.version());
    assertThat(prepared.path("artifactDigest").asText())
        .as("pinned release bundle SHA-256 %s:%s", pin.moduleId(), pin.version())
        .isEqualTo("sha256:" + pin.releaseDigestHex());
    return prepared.path("requestId").asText();
  }

  private static void assertPinned(JsonNode enabled, PinnedRelease pin) {
    assertThat(enabled)
        .as("pinned release %s:%s must be enabled", pin.moduleId(), pin.version())
        .isNotNull();
    assertThat(enabled.path("version").asText())
        .as("pinned version for %s", pin.moduleId())
        .isEqualTo(pin.version());
    assertThat(enabled.path("artifactDigest").asText())
        .as("installed module JAR SHA-256 %s:%s", pin.moduleId(), pin.version())
        .isEqualTo("sha256:" + pin.installedArtifactDigestHex());
    assertThat(enabled.path("sourceRepository").asText())
        .as("pinned source repository for %s", pin.moduleId())
        .isEqualTo(pin.sourceRepository());
  }

  private static void assertPinnedModuleLoaded(
      SeaRuntime runtime, PinnedRelease pin, String providerId, List<String> tools) {
    var module =
        runtime.modules().stream()
            .filter(candidate -> candidate.descriptor().moduleId().equals(pin.moduleId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Pinned module "
                            + pin.moduleId()
                            + ":"
                            + pin.version()
                            + " was not loaded after restart"));
    assertThat(module.descriptor().version()).isEqualTo(pin.version());

    LoadedSeaProvider provider =
        runtime
            .findLoadedProvider(providerId)
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Pinned provider "
                            + providerId
                            + " for "
                            + pin.moduleId()
                            + ":"
                            + pin.version()
                            + " was not discovered after restart"));
    assertThat(provider.module().moduleId()).isEqualTo(pin.moduleId());
    assertThat(provider.provider().listTools())
        .as("tool parity for %s:%s", pin.moduleId(), pin.version())
        .extracting(SeaToolDescriptor::name)
        .containsExactlyInAnyOrderElementsOf(tools);
  }

  private static void proveFilesystemToolParity(ProviderToolOperations operations)
      throws IOException {
    Map<?, ?> write =
        executeSideEffecting(
            operations,
            FILESYSTEM_PROVIDER_ID,
            "writeFile",
            "{\"path\":\"notes/compat.txt\",\"content\":\"compat parity\"}");
    assertThat(write.get("bytes")).isNotNull();

    Path written = FILESYSTEM_ROOT.resolve("notes/compat.txt");
    assertThat(written).as("the writeFile tool must persist under the configured root").exists();
    assertThat(Files.readString(written)).isEqualTo("compat parity");

    var read =
        invoke(operations, FILESYSTEM_PROVIDER_ID, "readFile", "{\"path\":\"notes/compat.txt\"}");
    assertThat(read.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(read.result().success()).isTrue();
    assertThat(((Map<?, ?>) read.result().content()).get("content")).isEqualTo("compat parity");
  }

  private static void proveShoppingListPageAndToolParity(
      ProviderToolOperations operations, int httpPort) throws Exception {
    Map<?, ?> added =
        executeSideEffecting(
            operations, SHOPPING_PROVIDER_ID, "add_item", "{\"name\":\"Milk\",\"quantity\":\"2\"}");
    assertThat(added.get("text")).isEqualTo("Added Milk");
    assertThat(SHOPPING_DATABASE)
        .as("the add_item tool must persist the shopping-list database")
        .exists();

    String pagePath = "/apps/" + SHOPPING.moduleId() + "/" + SHOPPING.pageId();
    String page = get(httpPort, pagePath);
    assertThat(page)
        .as("the module page must reflect the state the tools persisted")
        .contains("Shopping List")
        .contains("Milk")
        .contains("2");

    String bought = post(httpPort, pagePath + "/items/bought", Map.of("name", "Milk"));
    assertThat(bought).contains("Bought Milk");
    assertThat(get(httpPort, pagePath))
        .as("the bought item must disappear from the page")
        .doesNotContain("value=\"Milk\"");

    var list = invoke(operations, SHOPPING_PROVIDER_ID, "list_items", "{}");
    assertThat(list.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(((Map<?, ?>) list.result().content()).get("text"))
        .as("the tool must observe the state the page changed")
        .isEqualTo("List is empty");

    var summary = invoke(operations, SHOPPING_PROVIDER_ID, "purchase_summary", "{}");
    assertThat(summary.result().success()).isTrue();
    Map<?, ?> summaryData = (Map<?, ?>) ((Map<?, ?>) summary.result().content()).get("data");
    List<?> purchases = (List<?>) summaryData.get("items");
    assertThat(purchases)
        .as("the page mark-bought action must record one purchase event")
        .singleElement()
        .isEqualTo(Map.of("name", "Milk", "count", 1));

    HttpResponse<String> unknown =
        postRaw(httpPort, pagePath + "/items/bought", Map.of("name", "Unknown"));
    assertThat(unknown.statusCode()).isEqualTo(404);
    assertThat(unknown.body()).contains("Item not found");
  }

  private static void proveBrokenToolFixtureFailsWithDiagnostics(
      ProviderToolOperations operations) {
    assertThatThrownBy(
            () ->
                invoke(
                    operations,
                    FILESYSTEM_PROVIDER_ID,
                    "readFile",
                    "{\"path\":\"../../etc/passwd\"}"))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception -> {
              assertThat(exception.code())
                  .isEqualTo(ProviderToolOperationException.Code.VALIDATION);
              assertThat(exception.getMessage())
                  .as("a deliberately invalid tool input must fail with actionable diagnostics")
                  .contains("escapes provider root");
            });
  }

  private static void proveMissingModulePageFails(int httpPort) throws Exception {
    HttpResponse<String> missing =
        getRaw(httpPort, "/apps/" + SHOPPING.moduleId() + "/not-a-registered-page");
    assertThat(missing.statusCode())
        .as("a required module page that is absent must fail with 404, not silently pass")
        .isEqualTo(404);
  }

  private void assertEnabledRegistryStillPinnedAfterFailures() throws IOException {
    assertPinned(enabledModule(FILESYSTEM.moduleId()), FILESYSTEM);
    assertPinned(enabledModule(SHOPPING.moduleId()), SHOPPING);
    assertPinned(enabledModule(DOCKER.moduleId()), DOCKER);
    assertPinned(enabledModule(HOME_ASSISTANT.moduleId()), HOME_ASSISTANT);
  }

  private static Map<?, ?> executeSideEffecting(
      ProviderToolOperations operations, String providerId, String tool, String arguments) {
    ProviderToolOperations.ToolInvocationOutcome pending =
        invoke(operations, providerId, tool, arguments);
    assertThat(pending.status())
        .as("%s/%s must request confirmation", providerId, tool)
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL);

    ProviderToolOperations.ToolInvocationOutcome executed =
        operations.allowUnscoped(pending.approval().requestId());
    assertThat(executed.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(executed.result().success()).isTrue();
    return (Map<?, ?>) executed.result().content();
  }

  private static ProviderToolOperations.ToolInvocationOutcome invoke(
      ProviderToolOperations operations, String providerId, String tool, String arguments) {
    return operations.invoke(
        ProviderToolOperations.ToolInvocationCommand.operator(
            providerId, tool, arguments, new InvocationContext("module-compat", false, Map.of())));
  }

  private String post(String path) throws Exception {
    return post(path, Map.of());
  }

  private String post(String path, Map<String, String> form) throws Exception {
    HttpResponse<String> response = postRaw(port, path, form);
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  private static String post(int httpPort, String path, Map<String, String> form) throws Exception {
    HttpResponse<String> response = postRaw(httpPort, path, form);
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  private static HttpResponse<String> postRaw(int httpPort, String path, Map<String, String> form)
      throws Exception {
    String body =
        form.entrySet().stream()
            .map(
                entry ->
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + path))
            .timeout(Duration.ofMinutes(3))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String get(int httpPort, String path) throws Exception {
    HttpResponse<String> response = getRaw(httpPort, path);
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  private static HttpResponse<String> getRaw(int httpPort, String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + path))
            .timeout(Duration.ofMinutes(1))
            .GET()
            .build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String latestRequestId() throws IOException {
    return newestRequestFile().getFileName().toString().replace(".json", "");
  }

  private String latestRequestStatus() throws IOException {
    return JSON.readTree(newestRequestFile().toFile()).path("status").asText();
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
      if (moduleId.equals(entry.path("moduleId").asText())) return entry;
    }
    return null;
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-module-compat-");
      Files.writeString(
          workspace.resolve("AGENT.md"), "Module compatibility acceptance workspace.");
      Files.writeString(workspace.resolve("INFO.md"), "Disposable real-network workspace.");
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  record PinnedRelease(
      String moduleId,
      String version,
      String releaseDigestHex,
      String installedArtifactDigestHex,
      String sourceRepository,
      String pageId) {
    PinnedRelease withDigest(String replacement) {
      return new PinnedRelease(
          moduleId, version, replacement, replacement, sourceRepository, pageId);
    }
  }
}
