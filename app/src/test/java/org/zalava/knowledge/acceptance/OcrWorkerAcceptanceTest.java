package org.zalava.knowledge.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.catalog.FileSystemModuleConfigurationStore;
import org.zalava.catalog.ModuleConfigurationSnapshot;
import org.zalava.content.ContentExtractionFailureCategory;
import org.zalava.content.ContentExtractor;
import org.zalava.knowledge.application.KnowledgeEvidenceQueries;
import org.zalava.knowledge.application.KnowledgeExtractionJob;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.domain.DerivationState;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.runtime.SeaRuntime;
import org.zalava.support.PostgreSqlTestDatabase;
import org.zalava.support.RestartableSeaApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in real {@code sea-ocr-worker} acceptance lane.
 *
 * <p>Installs the indexed {@code sea-module-tika} bundle into a disposable SEA workspace, points
 * its service factory at a real {@code sea-ocr-worker} container on a loopback port, restarts, and
 * proves real worker-backed scanned extraction through SEA's owned source lifecycle, including
 * citation identity, sharing, revocation and confirmed deletion. It then drives real worker
 * unavailable/empty/limit outcomes plus a deterministic transport timeout, asserting that every
 * failure records a typed category, never promotes its candidate and retains the previous active
 * derivation. Finally it proves worker-disable fallback to the ordinary Tika path without replacing
 * prior evidence.
 *
 * <p>This lane never touches the live SEA container, never rebuilds the released worker or module,
 * and never adds an OCR engine to SEA. It is excluded from {@code :app:check}.
 */
@Tag("ocr-worker")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OcrWorkerAcceptanceTest {

  static final String MODULE_ID = "sea-module-tika";
  static final String VERSION = "1.1.0";
  static final String ARTIFACT_DIGEST =
      "sha256:daf22a559661e3dd79eef4e3a62eaef7be5e92b783afae14dc9796b9099bd3cd";
  static final String MODULE_JAR_DIGEST =
      "sha256:a5a2a8f7871ec9938ed2ca1500b35a401f2745a5aa67cbd864a098ab2a9376e4";
  static final String BOOTSTRAP_LOGIN = "knowledge-admin";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private static final Path WORKSPACE = createWorkspace();

  private static GenericContainer<?> worker;
  private static String workerUrl;
  private static ConfigurableApplicationContext restarted;
  private static KnowledgeSourceId retentionSource;
  private static Actor reader;

  @Autowired FileSystemModuleConfigurationStore configurations;
  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("sea.module-configuration.root", () -> WORKSPACE.toString());
    registry.add("sea.accounts.security-enabled", () -> "false");
    registry.add("sea.accounts.bootstrap-login", () -> BOOTSTRAP_LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> "OcrAcceptancePassword-123");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("sea.catalog.github.token", () -> credential("github-token"));
    registry.add(
        "sea.catalog.github-packages.username", () -> credential("github-packages-username"));
    registry.add("sea.catalog.github-packages.token", () -> credential("github-packages-token"));
    PostgreSqlTestDatabase.register(registry);
  }

  @BeforeAll
  static void startRealWorker() {
    requireCredentials();
    worker =
        new GenericContainer<>(DockerImageName.parse(workerImage()))
            .withExposedPorts(8080)
            .withTmpFs(Map.of("/tmp", "rw,size=128m"))
            .withCreateContainerCmdModifier(
                command ->
                    command.getHostConfig().withMemory(512L * 1024 * 1024).withPidsLimit(64L));
    worker.start();
    workerUrl = "http://localhost:" + worker.getMappedPort(8080);
  }

  @AfterAll
  static void stopRealWorker() {
    if (restarted != null) restarted.close();
    if (worker != null) worker.stop();
  }

  @Test
  @Order(1)
  void installsAndEnablesIndexedTikaAndConfiguresTheRealWorker() throws Exception {
    refreshAndSelect();
    String requestId = prepare();
    assertThat(post("/sea/control/module-release-installations/" + requestId + "/allow"))
        .contains("Module enabled");
    assertThat(latestRequestStatus()).isEqualTo("SUCCEEDED");

    JsonNode enabled = enabledModule(MODULE_ID);
    assertThat(enabled).isNotNull();
    assertThat(enabled.path("version").asText()).isEqualTo(VERSION);
    assertThat(enabled.path("artifactDigest").asText()).isEqualTo(MODULE_JAR_DIGEST);
    assertThat(latestRequestArtifactDigest())
        .as("the release-index bundle artifact SHA-256 the installer verified")
        .isEqualTo(ARTIFACT_DIGEST);

    configurations.saveCandidate(snapshot(workerConfiguration(workerUrl, "eng")), Map.of());
    assertThat(configurations.status(MODULE_ID))
        .isEqualTo(org.zalava.ModuleConfigurationStatus.RESTART_REQUIRED);
  }

  @Test
  @Order(2)
  void restartsLoadsTheExtractorAndProvesRealScannedExtractionAndLifecycle() throws Exception {
    restart(Map.of("sea.knowledge.maximum-text-characters", "1000000"));
    assertThat(extractorLoaded()).isTrue();

    KnowledgeSource source =
        register("scanned-invoice.png", "image/png", fixture("scanned-invoice.png"));
    retentionSource = source.id();
    extract(source.id());

    KnowledgeDerivation active = activeDerivation(source.id());
    assertThat(active.version()).isEqualTo(1);
    assertThat(active.processorId()).isEqualTo(MODULE_ID);
    assertThat(extractionText(source.id(), 1))
        .as("the real worker must OCR the scanned raster that digital Tika cannot read")
        .contains("SEA OCR ACCEPTANCE")
        .contains("HOUSEHOLD INVOICE 89 EUROS");
    assertThat(lifecycle().requireOwned(owner(), source.id()).processingState())
        .isEqualTo(SourceProcessingState.READY);
    assertThat(citation(source.id())).contains("derivationVersion\":1");

    KnowledgeSource shared =
        register("shared-receipt.png", "image/png", fixture("scanned-invoice.png"));
    extract(shared.id());
    exerciseSharingRevocationAndDeletion(shared);
  }

  @Test
  @Order(3)
  void deterministicTransportTimeoutRetainsThePriorDerivation() throws Exception {
    try (DelayedWorker delayed = new DelayedWorker()) {
      configurations.saveCandidate(snapshot(workerConfiguration(delayed.url(), "eng")), Map.of());
      restart(Map.of("sea.knowledge.maximum-text-characters", "1000000"));

      extract(retentionSource);

      assertThat(failureCategory(retentionSource, 2))
          .as(
              "the released Tika client must map a worker exceeding its request deadline to"
                  + " TIMED_OUT")
          .isEqualTo(ContentExtractionFailureCategory.TIMED_OUT);
      assertPriorDerivationRetained(retentionSource, 1);
    }
  }

  @Test
  @Order(4)
  void realWorkerOutputLimitRetainsThePriorDerivation() throws Exception {
    configurations.saveCandidate(snapshot(workerConfiguration(workerUrl, "eng")), Map.of());
    restart(Map.of("sea.knowledge.maximum-text-characters", "10"));

    extract(retentionSource);

    assertThat(failureCategory(retentionSource, 3))
        .as("real worker text above the host bound must map to OUTPUT_LIMIT_EXCEEDED")
        .isEqualTo(ContentExtractionFailureCategory.OUTPUT_LIMIT_EXCEEDED);
    assertPriorDerivationRetained(retentionSource, 1);
  }

  @Test
  @Order(5)
  void realWorkerEmptyResultRecordsUnavailableAndNeverMarksTheSourceProcessed() throws Exception {
    KnowledgeSource blank = register("blank-scan.png", "image/png", fixture("blank-scan.png"));
    extract(blank.id());

    assertThat(failureCategory(blank.id(), 1))
        .as("a real worker EMPTY_RESULT must map to the typed UNAVAILABLE category")
        .isEqualTo(ContentExtractionFailureCategory.UNAVAILABLE);
    assertThat(lifecycle().requireOwned(owner(), blank.id()).processingState())
        .as("an empty worker result must never mark the source processed")
        .isEqualTo(SourceProcessingState.FAILED);
    assertThat(derivations().active(blank.id())).isEmpty();
  }

  @Test
  @Order(6)
  void realWorkerUnavailableRetainsThePriorDerivation() throws Exception {
    String unavailableUrl = workerUrl;
    if (worker != null) worker.stop();

    configurations.saveCandidate(snapshot(workerConfiguration(unavailableUrl, "eng")), Map.of());
    restart(Map.of("sea.knowledge.maximum-text-characters", "1000000"));

    extract(retentionSource);

    assertThat(failureCategory(retentionSource, 4))
        .as("a stopped real worker must map to the typed UNAVAILABLE category")
        .isEqualTo(ContentExtractionFailureCategory.UNAVAILABLE);
    assertPriorDerivationRetained(retentionSource, 1);
    worker = null;
  }

  @Test
  @Order(7)
  void disablingTheWorkerFallsBackToOrdinaryTikaWithoutReplacingPriorEvidence() throws Exception {
    configurations.saveCandidate(
        new ModuleConfigurationSnapshot(
            MODULE_ID,
            VERSION,
            MODULE_ID + "-" + VERSION,
            Map.of("services", Map.of("ocrLanguages", "eng")),
            Map.of()),
        Map.of());
    restart(Map.of("sea.knowledge.maximum-text-characters", "1000000"));
    assertThat(extractorLoaded()).isTrue();

    extract(retentionSource);
    assertThat(failureCategory(retentionSource, 5))
        .as("worker-disabled Tika cannot read the scanned raster, so no blank result may promote")
        .isEqualTo(ContentExtractionFailureCategory.UNAVAILABLE);
    assertPriorDerivationRetained(retentionSource, 1);

    KnowledgeSource digital =
        register(
            "notes.txt", "text/plain", "digital notes extraction".getBytes(StandardCharsets.UTF_8));
    extract(digital.id());
    assertThat(extractionText(digital.id(), 1))
        .as("the ordinary Tika path must still extract digital text while the worker is disabled")
        .contains("digital notes extraction");
  }

  private void exerciseSharingRevocationAndDeletion(KnowledgeSource source) {
    lifecycle().changeVisibility(owner(), source.id(), KnowledgeVisibility.GROUP_SHARED);
    assertThat(citation(source.id(), reader())).doesNotContain("NOT_FOUND");
    lifecycle().changeVisibility(owner(), source.id(), KnowledgeVisibility.PRIVATE);
    assertThat(citation(source.id(), reader())).contains("NOT_FOUND");
    lifecycle().hardDelete(owner(), source.id());
    assertThat(citation(source.id())).contains("NOT_FOUND");
    assertThat(derivations().active(source.id())).isEmpty();
  }

  private boolean extractorLoaded() {
    return runtime().findService(ContentExtractor.CONTRACT).isPresent();
  }

  private void assertPriorDerivationRetained(KnowledgeSourceId sourceId, long version) {
    assertThat(lifecycle().requireOwned(owner(), sourceId).processingState())
        .as("a failed extraction must not mark the source processed")
        .isEqualTo(SourceProcessingState.READY);
    assertThat(derivations().active(sourceId))
        .get()
        .satisfies(
            active -> {
              assertThat(active.version()).isEqualTo(version);
              assertThat(active.state()).isEqualTo(DerivationState.ACTIVE);
            });
    assertThat(extractionText(sourceId, version)).contains("SEA OCR ACCEPTANCE");
  }

  private KnowledgeSource register(String name, String contentType, byte[] bytes) {
    return lifecycle()
        .register(
            owner(), name + "-" + UUID.randomUUID().toString().substring(0, 8), contentType, bytes);
  }

  private void extract(KnowledgeSourceId sourceId) {
    restarted.getBean(KnowledgeExtractionJob.class).extract(sourceId);
  }

  private KnowledgeDerivation activeDerivation(KnowledgeSourceId sourceId) {
    return derivations().active(sourceId).orElseThrow();
  }

  private String extractionText(KnowledgeSourceId sourceId, long version) {
    return records().find(sourceId, version).orElseThrow().text();
  }

  private ContentExtractionFailureCategory failureCategory(
      KnowledgeSourceId sourceId, long version) {
    return records().find(sourceId, version).orElseThrow().failureCategory();
  }

  private String citation(KnowledgeSourceId sourceId) {
    return citation(sourceId, owner());
  }

  private String citation(KnowledgeSourceId sourceId, Actor actor) {
    return evidence()
        .source(actor, sourceId.value().toString())
        .map(
            value ->
                "{\"status\":\"OK\",\"derivationVersion\":"
                    + value.derivationVersion()
                    + ",\"excerpt\":\""
                    + value.excerpt()
                    + "\"}")
        .orElse("{\"status\":\"NOT_FOUND\"}");
  }

  private Actor owner() {
    return new Actor(accounts().findByLoginName(BOOTSTRAP_LOGIN).orElseThrow().id());
  }

  private Actor reader() {
    if (reader == null) {
      reader =
          new Actor(
              accounts().create("ocr-reader", "FixturePassword-123", AccountRole.MEMBER).id());
    }
    return reader;
  }

  private AccountLifecycle accounts() {
    return restarted.getBean(AccountLifecycle.class);
  }

  private KnowledgeSourceLifecycle lifecycle() {
    return restarted.getBean(KnowledgeSourceLifecycle.class);
  }

  private KnowledgeDerivationStore derivations() {
    return restarted.getBean(KnowledgeDerivationStore.class);
  }

  private KnowledgeExtractionRecordStore records() {
    return restarted.getBean(KnowledgeExtractionRecordStore.class);
  }

  private KnowledgeEvidenceQueries evidence() {
    return restarted.getBean(KnowledgeEvidenceQueries.class);
  }

  private SeaRuntime runtime() {
    return restarted.getBean(SeaRuntime.class);
  }

  private ModuleConfigurationSnapshot snapshot(Map<String, Object> serviceConfiguration) {
    return new ModuleConfigurationSnapshot(
        MODULE_ID,
        VERSION,
        MODULE_ID + "-" + VERSION,
        Map.of("services", serviceConfiguration),
        Map.of());
  }

  private static Map<String, Object> workerConfiguration(String url, String languages) {
    return Map.of("ocrWorkerUrl", url, "ocrLanguages", languages);
  }

  private void restart(Map<String, String> extra) {
    if (restarted != null) restarted.close();
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("sea.module-configuration.root", WORKSPACE.toString());
    properties.putAll(extra);
    restarted = RestartableSeaApplicationContext.start(WORKSPACE, properties);
  }

  private static byte[] fixture(String name) {
    try (var input = OcrWorkerAcceptanceTest.class.getResourceAsStream("/ocr/" + name)) {
      assertThat(input).as("fixture %s must be on the test classpath", name).isNotNull();
      return input.readAllBytes();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read fixture " + name, exception);
    }
  }

  private static String workerImage() {
    return System.getProperty("sea.ocr-worker.image", "sea-ocr-worker:ci");
  }

  private static String credential(String suffix) {
    String property = System.getProperty("sea.indexed-install." + suffix);
    if (property == null || property.isBlank()) {
      property =
          System.getenv(
              "SEA_INDEXED_INSTALL_" + suffix.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    }
    return property == null ? "" : property.strip();
  }

  private static void requireCredentials() {
    assertThat(credential("github-token")).as("ZALAVA_INDEXED_INSTALL_PUBLISH_TOKEN").isNotBlank();
    assertThat(credential("github-packages-username"))
        .as("SEA_INDEXED_INSTALL_GITHUB_PACKAGES_USERNAME")
        .isNotBlank();
    assertThat(credential("github-packages-token"))
        .as("SEA_INDEXED_INSTALL_GITHUB_PACKAGES_TOKEN")
        .isNotBlank();
  }

  private void refreshAndSelect() throws Exception {
    assertThat(post("/sea/control/module-release-installations/catalog/refresh"))
        .contains("Refresh catalog");
    assertThat(
            post(
                "/sea/control/module-release-installations/catalog/select",
                Map.of("moduleId", MODULE_ID)))
        .contains(MODULE_ID);
  }

  private String prepare() throws Exception {
    String response =
        post(
            "/sea/control/module-release-installations",
            Map.of("moduleId", MODULE_ID, "version", VERSION));
    assertThat(response).contains(MODULE_ID);
    return latestRequestId();
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
    return JSON.readTree(newestRequestFile().toFile()).path("status").asText();
  }

  private String latestRequestArtifactDigest() throws IOException {
    return JSON.readTree(newestRequestFile().toFile()).path("artifactDigest").asText();
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
      Path workspace = Files.createTempDirectory("sea-ocr-acceptance-");
      Files.writeString(workspace.resolve("AGENT.md"), "OCR worker acceptance workspace.");
      Files.writeString(workspace.resolve("INFO.md"), "Disposable real-worker workspace.");
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  /**
   * Deterministic loopback transport that never answers within the released Tika client deadline.
   */
  private static final class DelayedWorker implements AutoCloseable {
    private final HttpServer server;

    DelayedWorker() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/v1/ocr",
          exchange -> {
            try {
              Thread.sleep(17_000);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
            byte[] body = "{\"text\":\"late\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
              output.write(body);
            }
          });
      server.setExecutor(
          Executors.newCachedThreadPool(
              runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
              }));
      server.start();
    }

    String url() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
