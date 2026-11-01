package org.zalava.packaging;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.sql.postgres.PostgresStorageProvider;
import org.jobrunr.utils.mapper.JsonMapperFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolCallbackNames;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.support.DigitalPdfFixture;
import org.zalava.tasks.adapter.in.jobrunr.TaskHandler;
import org.zalava.tasks.adapter.out.filesystem.ActorFileSystemTaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real released JARs, a packaged host process, real PostgreSQL and browser entry points. */
@Tag("released-module-distributable")
class ReleasedModuleDistributableAcceptanceTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String LOGIN = "released-module-admin";
  private static final String INITIAL_PASSWORD = "DisposableSdkPassword-123";
  private static final String PASSWORD = INITIAL_PASSWORD + "-changed";
  private final Path diagnostics =
      Path.of("build/reports/released-module-acceptance", UUID.randomUUID().toString())
          .toAbsolutePath();
  private final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private Process process;
  String ociImage;
  boolean ociCompose;
  private OciComposeFleet ociComposeFleet;
  private String ociContainer;
  private CatalogReleaseProxy catalogProxy;
  private String baseUrl;
  private int generation;

  @Test
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void uploadsReleasedFleetAndPreservesAuthorizedEffectsAcrossPackagedHostRestarts()
      throws Exception {
    Files.createDirectories(diagnostics);
    Path workspace = Files.createDirectories(diagnostics.resolve("workspace"));
    Files.writeString(workspace.resolve("AGENT.md"), "Disposable released-module acceptance.");
    List<Release> releases = downloadReleases();
    try (CatalogReleaseProxy proxy =
            new CatalogReleaseProxy(
                diagnostics,
                releases.stream()
                    .filter(release -> release.moduleId().equals("zalava-module-time"))
                    .findFirst()
                    .orElseThrow()
                    .path());
        PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
                .withDatabaseName("zalava_acceptance")
                .withUsername("zalava_acceptance")
                .withPassword(UUID.randomUUID().toString());
        Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext admin = browser.newContext()) {
      catalogProxy = proxy;
      postgres.start();
      Page page = admin.newPage();
      try {
        start(postgres, workspace, "dev");
        signIn(page, LOGIN, INITIAL_PASSWORD, true);
        try (BrowserContext anonymous = browser.newContext()) {
          APIResponse rejected =
              anonymous
                  .request()
                  .post(
                      baseUrl + "/modules/upload-installations",
                      RequestOptions.create().setMaxRedirects(0));
          assertThat(rejected.status()).isEqualTo(403);
          APIResponse protectedPage =
              anonymous
                  .request()
                  .get(baseUrl + "/modules", RequestOptions.create().setMaxRedirects(0));
          assertThat(protectedPage.status()).isEqualTo(302);
          assertThat(URI.create(protectedPage.headers().get("location")).getPath().split(";", 2)[0])
              .isEqualTo("/login");
        }
        page.navigate(baseUrl + "/zalava/accounts");
        page.locator("form[action='/zalava/accounts/create'] input[name=loginName]")
            .fill("released-member");
        page.locator("form[action='/zalava/accounts/create'] input[name=temporaryPassword]")
            .fill(INITIAL_PASSWORD);
        page.locator("#confirm-create-password").fill(INITIAL_PASSWORD);
        page.locator("form[action='/zalava/accounts/create'] select[name=role]")
            .selectOption("MEMBER");
        page.locator("form[action='/zalava/accounts/create'] button[type=submit]").click();
        try (BrowserContext member = browser.newContext()) {
          Page memberPage = member.newPage();
          signIn(memberPage, "released-member", INITIAL_PASSWORD, true);
          assertThat(member.request().get(baseUrl + "/modules").status()).isEqualTo(403);
          assertThat(member.request().get(baseUrl + "/api/zalava/modules").status()).isEqualTo(403);
          assertThat(post(member, memberPage, "/modules/catalog/refresh", Map.of()).status())
              .isEqualTo(403);
          assertThat(
                  post(
                          member,
                          memberPage,
                          "/modules/installation-requests",
                          Map.of("moduleId", "zalava-module-time", "version", "0.1.0-alpha.4"))
                      .status())
              .isEqualTo(403);
        }
        page.navigate(baseUrl + "/modules");
        if (page.locator("[data-module-developer-options]").getAttribute("open") == null) {
          page.locator("[data-module-developer-options] summary").click();
        }
        page.locator("input[name=moduleJar]")
            .setInputFiles(
                new FilePayload("invalid.jar", "application/java-archive", new byte[] {1}));
        page.locator("[data-action=upload-module]").click();
        page.getByRole(AriaRole.ALERT).waitFor();
        installFromCatalog(
            page,
            admin,
            releases.stream()
                .filter(release -> release.moduleId().equals("zalava-module-time"))
                .findFirst()
                .orElseThrow());
        for (Release release : releases) {
          if (release.moduleId().equals("zalava-module-time")) continue;
          page.navigate(baseUrl + "/modules");
          if (page.locator("[data-module-developer-options]").getAttribute("open") == null) {
            page.locator("[data-module-developer-options] summary").click();
          }
          page.locator("input[name=moduleJar]").setInputFiles(release.path());
          page.locator("[data-action=upload-module]").click();
          page.getByText("Uploaded " + release.moduleId() + " " + release.version() + " installed")
              .waitFor();
        }
        var jobEvidence = seedJobEvidence(postgres, workspace);
        assertJobEvidence(page, admin, browser, jobEvidence);
        stop();
        start(postgres, workspace, "dev");
        admin.clearCookies();
        signIn(page, LOGIN, PASSWORD, false);
        assertJobEvidence(page, admin, browser, jobEvidence);
        JsonNode modules =
            JSON.readTree(admin.request().get(baseUrl + "/api/zalava/modules").text());
        Path root = Files.createDirectories(workspace.resolve("filesystem-fixture"));
        Files.writeString(root.resolve("evidence.txt"), "Configured released filesystem");
        page.navigate(baseUrl + "/modules/zalava-module-filesystem");
        page.locator("textarea[name='configuration.filesystem-root.roots']")
            .fill(
                JSON.writeValueAsString(
                    List.of(
                        Map.of(
                            "id",
                            "acceptance",
                            "displayName",
                            "Acceptance fixture",
                            "path",
                            root.toString(),
                            "writable",
                            false))));
        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Save configuration").setExact(true))
            .click();
        startModule(page, "zalava-module-filesystem");
        APIResponse file =
            invoke(
                admin, page, "filesystem-acceptance", "readFile", Map.of("path", "evidence.txt"));
        assertThat(file.status()).isEqualTo(200);
        assertThat(file.text()).contains("Configured released filesystem");
        startModule(page, "zalava-module-time");
        startModule(page, "zalava-module-shopping-list");
        APIResponse time = invoke(admin, page, "jdk-time", "current_time", Map.of("zoneId", "UTC"));
        assertThat(time.status()).isEqualTo(200);
        assertThat(JSON.readTree(time.text()).path("success").booleanValue()).isTrue();
        assertThat(time.text()).contains("UTC");
        APIResponse invalidTime =
            invoke(admin, page, "jdk-time", "current_time", Map.of("zoneId", "Invalid/Zone"));
        assertThat(invalidTime.status()).isEqualTo(200);
        assertThat(JSON.readTree(invalidTime.text()).path("success").booleanValue()).isFalse();
        assertThat(invalidTime.text()).contains("error");

        APIResponse denied =
            invoke(
                admin, page, "shopping-list-household", "add_item", Map.of("name", "Denied milk"));
        assertThat(denied.status()).isEqualTo(202);
        String deniedId = JSON.readTree(denied.text()).path("requestId").stringValue();
        assertThat(
                post(admin, page, "/api/zalava/permission-requests/" + deniedId + "/deny", Map.of())
                    .status())
            .isEqualTo(200);
        page.navigate(baseUrl + "/apps/zalava-module-shopping-list/shopping-list");
        assertThat(page.content()).doesNotContain("Denied milk");
        APIResponse pending =
            invoke(
                admin,
                page,
                "shopping-list-household",
                "add_item",
                Map.of("name", "SDK acceptance milk"));
        assertThat(pending.status()).isEqualTo(202);
        String requestId = JSON.readTree(pending.text()).path("requestId").stringValue();
        assertThat(
                post(
                        admin,
                        page,
                        "/api/zalava/permission-requests/" + requestId + "/allow",
                        Map.of())
                    .status())
            .isEqualTo(200);
        page.navigate(baseUrl + "/apps/zalava-module-shopping-list/shopping-list");
        page.getByText("SDK acceptance milk", new Page.GetByTextOptions().setExact(true)).waitFor();
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(diagnostics.resolve("shopping-before-restart.png")));

        stop();
        start(postgres, workspace, "local-control");
        admin.clearCookies();
        signIn(page, LOGIN, PASSWORD, false);
        assertJobEvidence(page, admin, browser, jobEvidence);
        page.navigate(baseUrl + "/apps/zalava-module-shopping-list/shopping-list");
        page.getByText("SDK acceptance milk", new Page.GetByTextOptions().setExact(true)).waitFor();
        assertThat(admin.request().get(baseUrl + "/api/zalava/modules").status()).isEqualTo(404);
        page.locator("form:has(input[name=name][value='SDK acceptance milk']) input[type=checkbox]")
            .click();
        page.waitForURL(baseUrl + "/apps/zalava-module-shopping-list/shopping-list/items/bought");
        assertThat(page.locator("main").textContent()).contains("Bought SDK acceptance milk");
        page.getByText("SDK acceptance milk", new Page.GetByTextOptions().setExact(true))
            .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
        stop();
        start(postgres, workspace, "local-control");
        admin.clearCookies();
        signIn(page, LOGIN, PASSWORD, false);
        assertJobEvidence(page, admin, browser, jobEvidence);
        page.navigate(baseUrl + "/apps/zalava-module-shopping-list/shopping-list");
        assertThat(page.content()).doesNotContain("SDK acceptance milk", "Denied milk");
        assertThat(Files.size(workspace.resolve("shopping-list.sqlite"))).isPositive();
        page.navigate(baseUrl + "/modules/zalava-module-filesystem");
        assertThat(
                page.locator("textarea[name='configuration.filesystem-root.roots']").inputValue())
            .contains(root.toString());
        // Keep the complete fleet assertion after independent journeys so release defects
        // do not conceal other failures. Missing/incompatible artifacts still fail this lane.
        for (Release release : releases) {
          assertThat(modules.toString())
              .as("released module loaded after restart: %s", release.moduleId())
              .contains(release.moduleId());
        }
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(diagnostics.resolve("shopping-after-restart.png")));
      } catch (Throwable failure) {
        try {
          page.screenshot(new Page.ScreenshotOptions().setPath(diagnostics.resolve("failure.png")));
          Files.writeString(diagnostics.resolve("failure.html"), page.content());
        } catch (RuntimeException ignored) {
          // Process logs remain available when the page is already closed.
        }
        throw failure;
      } finally {
        stop();
      }
    }
  }

  private JobEvidenceFixture seedJobEvidence(PostgreSQLContainer postgres, Path workspace)
      throws Exception {
    var dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    UUID accountId;
    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement("SELECT id FROM zalava_account WHERE login_name = ?")) {
      statement.setString(1, LOGIN);
      try (var row = statement.executeQuery()) {
        assertThat(row.next()).isTrue();
        accountId = row.getObject(1, UUID.class);
      }
    }
    var actor = new Actor(new AccountId(accountId));
    var store = new ActorFileSystemTaskStore(workspace);
    var report = ActorTaskReference.newReference();
    var future = ActorTaskReference.newReference();
    String output = "# Packaged job report\nPersisted across real host process restarts.";
    store.save(
        actor,
        report,
        Task.newTask("Packaged saved report", "Restart fixture")
            .withStatus(Task.Status.completed)
            .withFeedback(output));
    store.save(actor, future, Task.newTask("Packaged future job", "Restart fixture"));
    var due = Instant.parse("2050-01-02T12:00:00Z");
    try (var storage = new PostgresStorageProvider(dataSource)) {
      storage.setJobMapper(new JobMapper(JsonMapperFactory.createJsonMapper()));
      var scheduler = new JobScheduler(storage);
      String token = new ActorTaskExecutionReference(actor, future).encode();
      scheduler.<TaskHandler>schedule(due, handler -> handler.executeTask(token, JobContext.Null));
    }
    return new JobEvidenceFixture(report.value(), future.value(), due, output);
  }

  private void assertJobEvidence(
      Page page, BrowserContext admin, Browser browser, JobEvidenceFixture fixture) {
    page.navigate(baseUrl + "/jobs");
    page.locator("[aria-labelledby='saved-reports-title']")
        .getByText("Packaged saved report", new Locator.GetByTextOptions().setExact(true))
        .waitFor();
    page.locator("[aria-labelledby='upcoming-jobs-title']")
        .getByText("Packaged future job", new Locator.GetByTextOptions().setExact(true))
        .waitFor();
    assertThat(page.locator("time[datetime='" + fixture.due() + "']").count()).isEqualTo(1);
    var download = admin.request().get(baseUrl + "/jobs/" + fixture.report() + "/artifacts/report");
    assertThat(download.status()).isEqualTo(200);
    assertThat(download.text()).isEqualTo(fixture.output());
    assertThat(download.headers().get("content-disposition")).contains("attachment");
    try (BrowserContext member = browser.newContext()) {
      var other = member.newPage();
      signIn(other, "released-member", PASSWORD, false);
      other.navigate(baseUrl + "/jobs");
      assertThat(other.locator("body").innerText())
          .doesNotContain("Packaged saved report", "Packaged future job");
      assertThat(
              member
                  .request()
                  .get(baseUrl + "/jobs/" + fixture.report() + "/artifacts/report")
                  .status())
          .isEqualTo(404);
    }
  }

  private record JobEvidenceFixture(String report, String future, Instant due, String output) {}

  private List<Release> downloadReleases() throws Exception {
    return downloadReleases(Set.of());
  }

  private List<Release> downloadReleases(Set<String> selected) throws Exception {
    Properties pins = new Properties();
    try (var input = getClass().getResourceAsStream("/released-modules.properties")) {
      assertThat(input).as("immutable released-module pins").isNotNull();
      pins.load(input);
    }
    assertThat(pins).hasSize(12);
    List<Release> result = new ArrayList<>();
    for (String name :
        pins.stringPropertyNames().stream()
            .filter(name -> selected.isEmpty() || selected.contains(name))
            .sorted()
            .toList()) {
      String[] pin = pins.getProperty(name).split(",");
      String moduleId = "zalava-module-" + name;
      String asset = moduleId + "-" + pin[0] + ".jar";
      URI uri =
          URI.create(
              "https://github.com/Zalava/"
                  + moduleId
                  + "/releases/download/v"
                  + pin[0]
                  + "/"
                  + asset);
      HttpResponse<byte[]> response = download(uri);
      assertThat(response.statusCode()).as("released artifact %s", uri).isEqualTo(200);
      String digest =
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body()));
      assertThat(digest).as("immutable digest %s", moduleId).isEqualTo(pin[1]);
      Path path = diagnostics.resolve(asset);
      Files.write(path, response.body());
      result.add(new Release(moduleId, pin[0], path));
    }
    return result;
  }

  private HttpResponse<byte[]> download(URI uri) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build();
    for (int attempt = 1; ; attempt++) {
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (attempt == 3 || !List.of(502, 503, 504).contains(response.statusCode())) {
        return response;
      }
      Files.writeString(
          diagnostics.resolve("download-retries.log"),
          uri + " attempt=" + attempt + " status=" + response.statusCode() + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
      Thread.sleep(1000L * attempt);
    }
  }

  private ProcessBuilder hostProcessBuilder(String... command) {
    List<String> arguments = new ArrayList<>(List.of(command));
    if (catalogProxy == null) {
      arguments.removeIf(
          argument ->
              argument.startsWith("-Dhttps.proxy")
                  || argument.startsWith("-Djavax.net.ssl.trustStore"));
    }
    if (ociImage != null) {
      ociContainer = "zalava-oci-fleet-" + UUID.randomUUID();
      List<String> docker =
          new ArrayList<>(
              List.of(
                  "docker",
                  "run",
                  "--rm",
                  "--name",
                  ociContainer,
                  "--network",
                  "host",
                  "--memory",
                  "2g",
                  "--cpus",
                  "2",
                  "--user",
                  fixtureUser(),
                  "--group-add",
                  "1001",
                  "--volume",
                  diagnostics + ":" + diagnostics));
      if (catalogProxy != null) {
        try {
          Path bindings =
              Files.createDirectories(diagnostics.resolve("certificate-bindings/proxy"));
          Files.writeString(bindings.resolve("type"), "ca-certificates");
          KeyStore trust = KeyStore.getInstance("PKCS12");
          try (var input = Files.newInputStream(catalogProxy.trustStore())) {
            trust.load(input, "fixture".toCharArray());
          }
          byte[] certificate = trust.getCertificate(trust.aliases().nextElement()).getEncoded();
          Files.writeString(
              bindings.resolve("proxy.pem"),
              "-----BEGIN CERTIFICATE-----\n"
                  + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate)
                  + "\n-----END CERTIFICATE-----\n");
          docker.addAll(List.of("--env", "SERVICE_BINDING_ROOT=" + bindings.getParent()));
        } catch (Exception failure) {
          throw new IllegalStateException(
              "Unable to prepare the disposable OCI certificate binding", failure);
        }
      }
      List<String> javaOptions = new ArrayList<>();
      for (String argument : arguments) {
        if (argument.startsWith("--")) {
          int equals = argument.indexOf('=');
          String key =
              argument
                  .substring(2, equals)
                  .replace('.', '_')
                  .replace('-', '_')
                  .toUpperCase(Locale.ROOT);
          docker.addAll(List.of("--env", key + argument.substring(equals)));
        } else if (argument.startsWith("-D") || argument.startsWith("-X")) {
          if (!argument.startsWith("-Djavax.net.ssl.trustStore")) javaOptions.add(argument);
        }
      }
      docker.addAll(
          List.of(
              "--env",
              "JAVA_TOOL_OPTIONS="
                  + (Boolean.parseBoolean(System.getProperty("oci.aot", "true"))
                      ? "-XX:AOTMode=on "
                      : "")
                  + String.join(" ", javaOptions),
              ociImage));
      if (ociCompose) {
        try {
          ociComposeFleet = new OciComposeFleet(ociImage, diagnostics, docker);
          ociContainer = null;
        } catch (Exception failure) {
          throw new IllegalStateException(
              "Unable to prepare the disposable Compose fleet", failure);
        }
      }
      arguments = docker;
    }
    ProcessBuilder builder =
        ociComposeFleet == null ? new ProcessBuilder(arguments) : ociComposeFleet.processBuilder();
    if (ociComposeFleet == null) {
      builder
          .environment()
          .keySet()
          .removeIf(
              key ->
                  key.startsWith("SPRING_")
                      || key.startsWith("AGENT_")
                      || key.startsWith("ZALAVA_")
                      || key.startsWith("SEA_")
                      || key.startsWith("JOBRUNR_"));
    }
    return builder;
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void discoversPublicDefaultsAndInstallsReleasedModuleAcrossRestart() throws Exception {
    Files.createDirectories(diagnostics);
    Path workspace = Files.createDirectories(diagnostics.resolve("workspace"));
    Files.writeString(workspace.resolve("AGENT.md"), "Disposable public catalog acceptance.");
    // Keep the production public default URLs and real released artifact, while
    // resolving immutable module-owned metadata through the existing HTTPS fixture.
    Release releasedTime = downloadReleases(Set.of("time")).getFirst();
    try (CatalogReleaseProxy proxy = new CatalogReleaseProxy(diagnostics, releasedTime.path());
        PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
                .withDatabaseName("zalava_public_catalog")
                .withUsername("zalava_public_catalog")
                .withPassword(UUID.randomUUID().toString());
        Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext admin = browser.newContext()) {
      catalogProxy = proxy;
      postgres.start();
      Page page = admin.newPage();
      try {
        start(postgres, workspace, "dev");
        signIn(page, LOGIN, INITIAL_PASSWORD, true);
        page.navigate(baseUrl + "/modules");
        page.locator("[data-action=refresh-catalog]").click();
        assertThat(page.locator("main").textContent()).contains("Catalog refreshed:");
        page.navigate(baseUrl + "/modules/zalava-module-time");
        String version = page.locator("[data-release-version]").inputValue();
        assertThat(version).isNotBlank();
        Files.writeString(diagnostics.resolve("public-release-version.txt"), version);
        page.locator("[data-action=request-installation]").click();
        page.navigate(baseUrl + "/monitoring#module-installations");
        page.locator("[data-installation-history]")
            .filter(new Locator.FilterOptions().setHasText("SUCCEEDED"))
            .waitFor();
        startModule(page, "zalava-module-time");
        assertThat(page.locator("main").textContent()).contains("zalava-module-time");
        page.screenshot(
            new Page.ScreenshotOptions().setPath(diagnostics.resolve("public-installation.png")));
        stop();
        admin.clearCookies();
        start(postgres, workspace, "dev");
        signIn(page, LOGIN, PASSWORD, false);
        page.navigate(baseUrl + "/modules/zalava-module-time");
        assertThat(page.locator("main").textContent()).contains("zalava-module-time", version);
        startModule(page, "zalava-module-time");
        APIResponse time = invoke(admin, page, "jdk-time", "current_time", Map.of("zoneId", "UTC"));
        assertThat(time.status()).isEqualTo(200);
        assertThat(time.text()).contains("UTC");
        page.screenshot(
            new Page.ScreenshotOptions().setPath(diagnostics.resolve("public-restart.png")));
      } catch (Throwable failure) {
        Files.writeString(diagnostics.resolve("public-catalog-failure.html"), page.content());
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(diagnostics.resolve("public-catalog-failure.png"))
                .setFullPage(true));
        throw failure;
      } finally {
        stop();
      }
    }
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void persistsProviderFromSettingsAcrossPackagedHostRestartAndAnswersChat() throws Exception {
    Files.createDirectories(diagnostics);
    Path workspace = Files.createDirectories(diagnostics.resolve("workspace"));
    Files.writeString(workspace.resolve("AGENT.md"), "Disposable native-provider acceptance.");
    WireMockServer modelServer = new WireMockServer(0);
    modelServer.start();
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        """
            data: {"id":"fixture","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Native fixture answer"},"finish_reason":null}]}

            data: {"id":"fixture","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """)));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(1)
            .withRequestBody(
                matchingJsonPath(
                    "$.messages[?(@.role == 'user')].content", containing("amber-larch-1013")))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        """
            data: {"id":"pdf-fixture","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Your PDF code is amber-larch-1013."},"finish_reason":null}]}

            data: {"id":"pdf-fixture","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """)));
    try (PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
                .withDatabaseName("provider_acceptance")
                .withUsername("provider_acceptance")
                .withPassword(UUID.randomUUID().toString());
        Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext admin = browser.newContext()) {
      postgres.start();
      Page page = admin.newPage();
      page.onPageError(
          error -> {
            try {
              Files.writeString(
                  diagnostics.resolve("browser-errors.private.txt"),
                  error + "\n",
                  StandardOpenOption.CREATE,
                  StandardOpenOption.APPEND);
            } catch (Exception ignored) {
            }
          });

      try {
        start(postgres, workspace, "test");
        signIn(page, LOGIN, INITIAL_PASSWORD, true);
        page.waitForURL("**/chat");
        assertThat(page.locator("#message").isDisabled()).isTrue();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Set up a provider"))
            .click();
        page.locator("#provider-choice").selectOption("openai-compatible");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Choose provider"))
            .click();
        page.locator("input[name=model]").fill("fixture-model");
        page.locator("input[name=apiKey]").fill("fixture-key");
        page.locator("input[name=baseUrl]").fill(modelServer.baseUrl() + "/v1");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save provider"))
            .click();
        page.getByText(
                "Provider configuration saved and applied.",
                new Page.GetByTextOptions().setExact(false))
            .waitFor();
        assertThat(page.locator("input[name=apiKey]").inputValue()).isEmpty();
        for (int width : new int[] {1440, 768, 390}) {
          page.setViewportSize(width, 900);
          if (width <= 1024)
            page.waitForFunction(
                "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
          assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
              .isTrue();
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(diagnostics.resolve("provider-applied-" + width + ".png"))
                  .setFullPage(true));
        }
        page.navigate(baseUrl + "/chat");
        page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
        assertThat(page.locator("#message").isDisabled()).isFalse();
        assertThat(page.locator(".provider-notice").count()).isZero();
        page.locator("#message").fill("Hello before restarting");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        page.locator(".message.assistant").getByText("Native fixture answer").waitFor();
        modelServer.verify(
            postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("fixture-model"))));
        page.navigate(baseUrl + "/settings?section=provider");
        page.locator("input[name=model]").fill("updated-fixture-model");
        // Blank credential preserves the saved secret and must not block native form submission.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save provider"))
            .click();
        page.getByText(
                "Provider configuration saved and applied.",
                new Page.GetByTextOptions().setExact(false))
            .waitFor();
        page.navigate(baseUrl + "/chat");
        page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
            .click();
        page.locator("#message").fill("Use the updated model immediately");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        page.locator(".message.assistant").getByText("Native fixture answer").waitFor();
        modelServer.verify(
            postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("updated-fixture-model")))
                .withHeader("Authorization", equalTo("Bearer fixture-key")));
        for (int width : new int[] {1440, 768, 390}) {
          page.setViewportSize(width, 900);
          if (width <= 1024)
            page.waitForFunction(
                "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(diagnostics.resolve("chat-live-provider-" + width + ".png"))
                  .setFullPage(true));
        }
        Release tika = downloadReleases(Set.of("tika")).getFirst();
        page.navigate(baseUrl + "/modules");
        if (page.locator("[data-module-developer-options]").getAttribute("open") == null) {
          page.locator("[data-module-developer-options] summary").click();
        }
        page.locator("input[name=moduleJar]").setInputFiles(tika.path());
        page.locator("[data-action=upload-module]").click();
        page.getByText("Uploaded " + tika.moduleId() + " " + tika.version() + " installed")
            .waitFor();
        // Use the production Actuator context restart with no external dispatcher.
        long pid = process.pid();
        page.navigate(baseUrl + "/modules");
        assertThat(page.locator("[data-action=restart-zalava]").isEnabled()).isTrue();
        assertThat(page.locator("[data-zalava-restart-status]").textContent())
            .contains("Ready to restart the Zalava application");
        for (int width : new int[] {1440, 768, 390}) {
          page.setViewportSize(width, 900);
          if (width <= 1024)
            page.waitForFunction(
                "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
          assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
              .isTrue();
          assertThat(
                  (Boolean)
                      page.locator(".managed-module-row")
                          .first()
                          .evaluate("e => e.scrollWidth<=e.clientWidth"))
              .as("installed module remains contained inside its compact row at %s", width)
              .isTrue();
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(diagnostics.resolve("application-restart-ready-" + width + ".png"))
                  .setFullPage(true));
        }
        // The response may finish before or during server shutdown; poll durable completion.
        try {
          page.locator("[data-action=restart-zalava]").click();
        } catch (PlaywrightException restarting) {
          assertThat(process.isAlive()).isTrue();
        }
        Path restartJournal =
            workspace
                .resolveSibling(workspace.getFileName() + "-application-control")
                .resolve("zalava-runtime-restart/request.properties");
        long restartDeadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        boolean completed = false;
        while (System.nanoTime() < restartDeadline) {
          assertThat(process.isAlive()).as("Actuator restart retains the packaged JVM").isTrue();
          if (Files.isRegularFile(restartJournal)) {
            Properties status = new Properties();
            try (var input = Files.newInputStream(restartJournal)) {
              status.load(input);
            }
            if ("COMPLETED".equals(status.getProperty("phase"))) {
              try {
                if (http.send(
                            HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                                .timeout(Duration.ofSeconds(2))
                                .GET()
                                .build(),
                            HttpResponse.BodyHandlers.discarding())
                        .statusCode()
                    == 200) {
                  completed = true;
                  break;
                }
              } catch (IOException notReady) {
                /* Context still opening its web server. */
              }
            }
          }
          Thread.sleep(250);
        }
        assertThat(completed).as("Actuator context restart completes").isTrue();
        try {
          page.waitForURL("**/login");
        } catch (PlaywrightException failure) {
          Files.writeString(diagnostics.resolve("restart-failure.html"), page.content());
          Files.writeString(diagnostics.resolve("restart-failure-url.txt"), page.url());
          page.screenshot(
              new Page.ScreenshotOptions().setPath(diagnostics.resolve("restart-failure.png")));
          if (page.locator("[data-restart-previous]").count() > 0) {
            String previous =
                page.locator("[data-restart-previous]").getAttribute("data-restart-previous");
            Files.writeString(
                diagnostics.resolve("restart-status.json"),
                admin.request().get(baseUrl + "/restart-status?previous=" + previous).text());
          }
          throw failure;
        }
        assertThat(page.locator("input[name=username]").isVisible()).isTrue();
        assertThat(process.pid()).isEqualTo(pid);
        assertThat(workspace.resolve("zalava-runtime-restart/request.properties")).doesNotExist();
        admin.clearCookies();
        signIn(page, LOGIN, PASSWORD, false);
        page.navigate(baseUrl + "/modules");
        assertThat(
                page.locator("[data-zalava-restart-status]")
                    .getAttribute("data-zalava-restart-status"))
            .isEqualTo("COMPLETED");
        for (int width : new int[] {1440, 768, 390}) {
          page.setViewportSize(width, 900);
          if (width <= 1024)
            page.waitForFunction(
                "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(diagnostics.resolve("application-restart-completed-" + width + ".png"))
                  .setFullPage(true));
        }
        page.navigate(baseUrl + "/chat");
        page.waitForURL("**/chat");
        page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
        assertThat(page.locator("#message").isDisabled()).isFalse();
        assertThat(page.locator(".provider-notice").count()).isZero();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
            .click();
        page.locator("#message").fill("Hello local fixture");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        page.getByText("Native fixture answer", new Page.GetByTextOptions().setExact(true))
            .waitFor();
        modelServer.verify(
            postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer fixture-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("updated-fixture-model")))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true"))));
        startModule(page, "zalava-module-tika");
        page.navigate(baseUrl + "/chat");
        page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
            .click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("File options"))
            .click();
        var chooser =
            page.waitForFileChooser(
                () ->
                    page.getByRole(
                            AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Attach files"))
                        .click());
        String pdfFact = "The PDF acceptance code is amber-larch-1013.";
        chooser.setFiles(
            new FilePayload(
                "acceptance.pdf", "application/pdf", DigitalPdfFixture.withText(pdfFact)));
        page.locator("[data-testid=composer] .attachment-chip")
            .getByText("acceptance.pdf")
            .waitFor();
        page.locator("#message").fill("What code does my PDF contain?");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        try {
          page.getByText(
                  "Your PDF code is amber-larch-1013.", new Page.GetByTextOptions().setExact(true))
              .waitFor();
        } catch (PlaywrightException failure) {
          Files.writeString(diagnostics.resolve("pdf-failure.html"), page.content());
          page.screenshot(
              new Page.ScreenshotOptions().setPath(diagnostics.resolve("pdf-failure.png")));
          Files.writeString(
              diagnostics.resolve("model-requests.private.txt"),
              modelServer.getAllServeEvents().stream()
                  .map(event -> event.getRequest().getBodyAsString())
                  .reduce("", (left, right) -> left + "\n" + right));
          throw failure;
        }
        modelServer.verify(
            postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(
                    matchingJsonPath(
                        "$.messages[?(@.role == 'user')].content",
                        containing("amber-larch-1013"))));
        String pdfConversation =
            page.getByRole(
                    AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("Select conversation"))
                .inputValue();
        page.reload();
        page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
        page.getByRole(
                AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("Select conversation"))
            .selectOption(pdfConversation);
        page.locator(".message.user .attachment-chip").getByText("acceptance.pdf").waitFor();
        assertThat(page.locator(".message.user p").allTextContents())
            .containsExactly("What code does my PDF contain?");
        assertThat(page.locator(".message.user").innerText())
            .doesNotContain("sha256", "bytes", "amber-larch-1013");
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(diagnostics.resolve("chat-released-tika-pdf.png")));
        page.navigate(baseUrl + "/settings?section=provider");
        assertThat(
                page.getByText("Restart required", new Page.GetByTextOptions().setExact(true))
                    .count())
            .isZero();
        assertThat(page.locator("input[name=model]").inputValue())
            .isEqualTo("updated-fixture-model");
        assertThat(page.locator("input[name=apiKey]").inputValue()).isEmpty();
        Release timeRelease = downloadReleases(Set.of("time")).getFirst();
        page.navigate(baseUrl + "/modules");
        page.locator("[data-module-developer-options] summary").click();
        page.locator("input[name=moduleJar]").setInputFiles(timeRelease.path());
        page.locator("[data-action=upload-module]").click();
        page.getByText(
                "Uploaded " + timeRelease.moduleId() + " " + timeRelease.version() + " installed")
            .waitFor();
        Release shoppingRelease = downloadReleases(Set.of("shopping-list")).getFirst();
        page.locator("[data-module-developer-options] summary").click();
        page.locator("input[name=moduleJar]").setInputFiles(shoppingRelease.path());
        page.locator("[data-action=upload-module]").click();
        page.getByText(
                "Uploaded "
                    + shoppingRelease.moduleId()
                    + " "
                    + shoppingRelease.version()
                    + " installed")
            .waitFor();
        stop();
        admin.clearCookies();
        start(postgres, workspace, "dev");
        signIn(page, LOGIN, PASSWORD, false);
        startModule(page, "zalava-module-time");
        startModule(page, "zalava-module-shopping-list");
        page.navigate(baseUrl + "/zalava/accounts");
        String clockMember = "released-clock-member";
        page.locator("#create-login").fill(clockMember);
        page.locator("#create-password").fill(INITIAL_PASSWORD);
        page.locator("#confirm-create-password").fill(INITIAL_PASSWORD);
        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Create account").setExact(true))
            .click();
        page.navigate(baseUrl + "/zalava/accounts/tool-defaults");
        var clockDefault = page.locator("[data-tool-access='jdk-time:current_time']");
        assertThat(
                clockDefault
                    .getByRole(
                        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save default"))
                    .isVisible())
            .isTrue();
        clockDefault.locator("select[name=membersAllowed]").selectOption("true");
        clockDefault
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save default"))
            .click();
        String clockCallback = ZalavaToolCallbackNames.forTool("jdk-time", "current_time");
        String clockCall =
            """
            data: {"id":"clock","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"released-clock-call","type":"function","function":{"name":"%s","arguments":%s}}]},"finish_reason":null}]}

            data: {"id":"clock","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

            data: [DONE]

            """
                .formatted(
                    clockCallback,
                    JSON.writeValueAsString(JSON.writeValueAsString(Map.of("zoneId", "UTC"))));
        modelServer.stubFor(
            WireMock.post(urlEqualTo("/v1/chat/completions"))
                .atPriority(2)
                .withRequestBody(
                    matchingJsonPath(
                        "$.messages[?(@.role == 'user')].content",
                        containing("Released Time UTC check")))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(clockCall)));
        modelServer.stubFor(
            WireMock.post(urlEqualTo("/v1/chat/completions"))
                .atPriority(1)
                .withRequestBody(matchingJsonPath("$.messages[?(@.role == 'tool')]"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(
                            """
            data: {"id":"clock-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Released Time call completed"},"finish_reason":null}]}

            data: {"id":"clock-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """)));
        try (var memberContext = browser.newContext()) {
          Page memberPage = memberContext.newPage();
          signIn(memberPage, clockMember, INITIAL_PASSWORD, true);
          memberPage.navigate(baseUrl + "/chat");
          memberPage.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
          memberPage.locator("#message").fill("Released Time UTC check");
          memberPage
              .getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
              .click();
          memberPage
              .getByText("Released Time call completed", new Page.GetByTextOptions().setExact(true))
              .waitFor();
          var answer = memberPage.locator(".message.assistant").last();
          answer.locator(".answer-execution > summary").click();
          answer.locator(".answer-tool > summary").click();
          assertThat(answer.innerText())
              .contains("jdk-time", "current_time", "zoneId", "UTC", "instant");
          memberPage.reload();
          memberPage.locator(".message.assistant .answer-execution > summary").last().click();
          memberPage.locator(".message.assistant .answer-tool > summary").last().click();
          assertThat(memberPage.locator(".message.assistant").last().innerText())
              .contains("jdk-time", "instant");
          memberPage.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(diagnostics.resolve("released-time-member-inline.png")));
        }
        proveDirectShoppingApprovals(page, modelServer);
        proveChatConfirmationSettings(page, browser, modelServer);
        proveUnifiedPermissions(page, modelServer);
        stop();
        start(postgres, workspace, "local-control", true);
        page.context().clearCookies();
        signIn(page, LOGIN, PASSWORD, false);
        proveKnowledgeReadConfirmation(page, modelServer);
        proveHostCallbackConfirmation(page, modelServer);
      } finally {
        stop();
      }
    } finally {
      modelServer.stop();
    }
  }

  private void proveDirectShoppingApprovals(Page page, WireMockServer modelServer)
      throws Exception {
    page.setViewportSize(1440, 900);
    String marker = "Released Shopping direct approval check";
    String callback = ZalavaToolCallbackNames.forTool("shopping-list-household", "add_item");
    String calls =
        """
        data: {"id":"shopping","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"shopping-milk","type":"function","function":{"name":"%s","arguments":%s}},{"index":1,"id":"shopping-denied","type":"function","function":{"name":"%s","arguments":%s}}]},"finish_reason":null}]}

        data: {"id":"shopping","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

        data: [DONE]

        """
            .formatted(
                callback,
                JSON.writeValueAsString(
                    JSON.writeValueAsString(Map.of("name", "Released approval milk"))),
                callback,
                JSON.writeValueAsString(
                    JSON.writeValueAsString(Map.of("name", "Released denied bread"))));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(2)
            .withRequestBody(
                matchingJsonPath("$.messages[?(@.role == 'user')].content", containing(marker)))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/event-stream").withBody(calls)));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(0)
            .withRequestBody(
                matchingJsonPath("$.messages[?(@.role == 'user')].content", containing(marker)))
            .withRequestBody(matchingJsonPath("$.messages[?(@.role == 'tool')]"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        """
        data: {"id":"shopping-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Shopping operations await permission."},"finish_reason":null}]}

        data: {"id":"shopping-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: [DONE]

        """)));
    page.navigate(baseUrl + "/chat");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
        .click();
    page.locator("#message").fill(marker);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    page.getByText(
            "Shopping operations await permission.", new Page.GetByTextOptions().setExact(true))
        .waitFor();
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions"))
        .waitFor();
    page.reload();
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions"))
        .click();
    var cards =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Pending permission"));
    var answer = page.locator(".message.assistant").last();
    answer.locator(".answer-execution > summary").click();
    PlaywrightAssertions.assertThat(answer.locator(".answer-tool > summary")).hasCount(2);
    PlaywrightAssertions.assertThat(answer.locator(".answer-tool > summary").first())
        .containsText("Awaiting permission");
    answer.locator(".answer-tool > summary").first().click();
    PlaywrightAssertions.assertThat(answer).containsText("pending_approval");
    // Sections with accessible names are exposed as regions by the browser.
    PlaywrightAssertions.assertThat(cards).hasCount(2);
    for (int width : new int[] {1440, 768, 390}) {
      page.setViewportSize(width, 900);
      if (width <= 1024)
        page.waitForFunction(
            "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
      assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
          .isTrue();
      assertThat((Boolean) cards.first().evaluate("e => e.scrollWidth<=e.clientWidth")).isTrue();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(diagnostics.resolve("shopping-direct-pending-" + width + ".png")));
    }
    page.setViewportSize(1440, 900);
    var milk = cards.filter(new Locator.FilterOptions().setHasText("Released approval milk"));
    var bread = cards.filter(new Locator.FilterOptions().setHasText("Released denied bread"));
    milk.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Allow once")).focus();
    page.keyboard().press("Enter");
    page.getByText("Permission allowed once; operation completed.").waitFor();
    PlaywrightAssertions.assertThat(cards).hasCount(1);
    bread
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Deny").setExact(true))
        .click();
    page.getByText("Permission denied; operation was not run.").waitFor();
    PlaywrightAssertions.assertThat(cards).hasCount(0);
    page.screenshot(
        new Page.ScreenshotOptions().setPath(diagnostics.resolve("shopping-direct-decided.png")));
    page.navigate(baseUrl + "/apps/zalava-module-shopping-list/shopping-list");
    PlaywrightAssertions.assertThat(
            page.getByText("Released approval milk", new Page.GetByTextOptions().setExact(true)))
        .hasCount(1);
    assertThat(page.content()).doesNotContain("Released denied bread");
    page.reload();
    PlaywrightAssertions.assertThat(
            page.getByText("Released approval milk", new Page.GetByTextOptions().setExact(true)))
        .hasCount(1);
    page.screenshot(
        new Page.ScreenshotOptions().setPath(diagnostics.resolve("shopping-direct-persisted.png")));
  }

  private void proveChatConfirmationSettings(Page page, Browser browser, WireMockServer modelServer)
      throws Exception {
    page.setViewportSize(1440, 900);
    page.navigate(baseUrl + "/zalava/accounts/chat-confirmations");
    var row = page.locator("[data-confirmation-tool='shopping-list-household:add_item']");
    PlaywrightAssertions.assertThat(row).containsText("Existing policy");
    row.locator("form[action$='/role']")
        .first()
        .locator("select[name=choice]")
        .selectOption("AUTO");
    row.getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save administrator default"))
        .focus();
    page.keyboard().press("Enter");
    page.getByText("Role confirmation default saved.").waitFor();
    for (int width : new int[] {1440, 768, 390}) {
      page.setViewportSize(width, 900);
      if (width <= 1024)
        page.waitForFunction(
            "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
      assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
          .isTrue();
      assertThat((Boolean) row.evaluate("e => e.scrollWidth<=e.clientWidth")).isTrue();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(diagnostics.resolve("chat-confirmation-role-" + width + ".png"))
              .setFullPage(true));
    }
    page.setViewportSize(1440, 900);
    page.reload();
    assertThat(
            row.locator("form[action$='/role']")
                .first()
                .locator("select[name=choice]")
                .inputValue())
        .isEqualTo("AUTO");
    String account =
        page.locator("#confirmation-account option")
            .filter(new Locator.FilterOptions().setHasText(LOGIN))
            .getAttribute("value");
    page.locator("#confirmation-account").selectOption(account);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show individual settings"))
        .click();
    PlaywrightAssertions.assertThat(row).containsText("Run without confirmation");
    PlaywrightAssertions.assertThat(row).containsText("Role default");
    row.locator("form[action$='/user'] select[name=choice]").selectOption("REQUIRE");
    row.getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save individual setting"))
        .click();
    PlaywrightAssertions.assertThat(row).containsText("Require confirmation · User override");
    for (int width : new int[] {1440, 768, 390}) {
      page.setViewportSize(width, 900);
      if (width <= 1024)
        page.waitForFunction(
            "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
      assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
          .isTrue();
      assertThat((Boolean) row.evaluate("e => e.scrollWidth<=e.clientWidth")).isTrue();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(diagnostics.resolve("chat-confirmation-individual-" + width + ".png"))
              .setFullPage(true));
    }
    page.setViewportSize(1440, 900);
    sendShoppingPolicyTurn(
        page, modelServer, "Confirmation-required bread", "Policy guarded bread");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions"))
        .click();
    var card =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Pending permission"));
    PlaywrightAssertions.assertThat(card).containsText("Policy guarded bread");
    card.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Deny").setExact(true))
        .click();
    page.getByText("Permission denied; operation was not run.").waitFor();
    page.navigate(baseUrl + "/zalava/accounts/chat-confirmations?accountId=" + account);
    row.locator("form[action$='/user'] select[name=choice]").selectOption("INHERIT");
    row.getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save individual setting"))
        .click();
    PlaywrightAssertions.assertThat(row).containsText("Run without confirmation · Role default");
    sendShoppingPolicyTurn(page, modelServer, "Confirmation-auto milk", "Policy automatic milk");
    PlaywrightAssertions.assertThat(
            page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions")))
        .hasCount(0);
    page.navigate(baseUrl + "/apps/zalava-module-shopping-list/shopping-list");
    PlaywrightAssertions.assertThat(
            page.getByText("Policy automatic milk", new Page.GetByTextOptions().setExact(true)))
        .hasCount(1);
    assertThat(page.content()).doesNotContain("Policy guarded bread");
    page.reload();
    PlaywrightAssertions.assertThat(
            page.getByText("Policy automatic milk", new Page.GetByTextOptions().setExact(true)))
        .hasCount(1);
    page.navigate(baseUrl + "/zalava/accounts/tool-defaults");
    var access = page.locator("[data-tool-access='shopping-list-household:add_item']");
    access.locator("select[name=membersAllowed]").selectOption("true");
    access
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save default"))
        .click();
    page.navigate(baseUrl + "/zalava/accounts/chat-confirmations");
    row.locator("form[action$='/role']").nth(1).locator("select[name=choice]").selectOption("AUTO");
    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save member default"))
        .click();
    try (var memberContext = browser.newContext()) {
      Page member = memberContext.newPage();
      signIn(member, "released-clock-member", PASSWORD, false);
      sendShoppingPolicyTurn(
          member, modelServer, "Member confirmation-auto", "Member automatic milk");
      PlaywrightAssertions.assertThat(
              member.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions")))
          .hasCount(0);
      member.navigate(baseUrl + "/apps");
      PlaywrightAssertions.assertThat(
              member.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Shopping List")))
          .hasCount(0);
      assertThat(
              member
                  .request()
                  .get(baseUrl + "/apps/zalava-module-shopping-list/shopping-list")
                  .status())
          .isEqualTo(403);
      page.navigate(baseUrl + "/zalava/accounts/app-access");
      var app = page.locator("[data-app-access='zalava-module-shopping-list:shopping-list']");
      PlaywrightAssertions.assertThat(app).containsText("Not allowed");
      for (int width : new int[] {1440, 768, 390}) {
        page.setViewportSize(width, 900);
        if (width <= 1024)
          page.waitForFunction(
              "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
        assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
            .isTrue();
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(diagnostics.resolve("app-access-" + width + ".png"))
                .setFullPage(true));
      }
      page.setViewportSize(1440, 900);
      app.locator("select[name=allowed]").selectOption("true");
      app.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save member default"))
          .focus();
      page.keyboard().press("Enter");
      page.getByText("Member default saved.").waitFor();
      page.reload();
      assertThat(app.locator("select[name=allowed]").inputValue()).isEqualTo("true");
      member.navigate(baseUrl + "/apps");
      member.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Shopping List")).click();
      PlaywrightAssertions.assertThat(
              member.getByText("Member automatic milk", new Page.GetByTextOptions().setExact(true)))
          .hasCount(1);
      for (int width : new int[] {1440, 768, 390}) {
        member.setViewportSize(width, 900);
        if (width <= 1024)
          member.waitForFunction(
              "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
        assertThat((Boolean) member.evaluate("document.documentElement.scrollWidth<=innerWidth"))
            .isTrue();
        member.screenshot(
            new Page.ScreenshotOptions()
                .setPath(diagnostics.resolve("member-shopping-" + width + ".png"))
                .setFullPage(true));
      }
      member.setViewportSize(1440, 900);
      member
          .locator("form:has(input[name=name][value='Member automatic milk']) input[type=checkbox]")
          .check();
      member.getByText("Bought Member automatic milk").waitFor();
      member.navigate(baseUrl + "/apps/zalava-module-shopping-list/shopping-list");
      PlaywrightAssertions.assertThat(
              member.getByText("Member automatic milk", new Page.GetByTextOptions().setExact(true)))
          .hasCount(0);
      page.navigate(baseUrl + "/zalava/accounts/app-access");
      app.locator("select[name=allowed]").selectOption("false");
      app.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save member default"))
          .click();
      assertThat(
              member
                  .request()
                  .get(baseUrl + "/apps/zalava-module-shopping-list/shopping-list")
                  .status())
          .isEqualTo(403);
      String memberId =
          page.locator("#member option")
              .filter(new Locator.FilterOptions().setHasText("released-clock-member"))
              .getAttribute("value");
      page.locator("#member").selectOption(memberId);
      page.getByRole(
              AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show individual settings"))
          .click();
      for (int width : new int[] {1440, 768, 390}) {
        page.setViewportSize(width, 900);
        if (width <= 1024)
          page.waitForFunction(
              "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
        assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
            .isTrue();
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(diagnostics.resolve("app-access-individual-" + width + ".png"))
                .setFullPage(true));
      }
      page.setViewportSize(1440, 900);
      app.locator("select[name=decision]").selectOption("ALLOW");
      app.getByRole(
              AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save individual access"))
          .click();
      page.getByText("Individual access saved.").waitFor();
      assertThat(
              member
                  .request()
                  .get(baseUrl + "/apps/zalava-module-shopping-list/shopping-list")
                  .status())
          .isEqualTo(200);
      app.locator("select[name=decision]").selectOption("DENY");
      app.getByRole(
              AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save individual access"))
          .click();
      assertThat(
              member
                  .request()
                  .get(baseUrl + "/apps/zalava-module-shopping-list/shopping-list")
                  .status())
          .isEqualTo(403);
    }
  }

  private void proveUnifiedPermissions(Page page, WireMockServer modelServer) throws Exception {
    page.navigate(baseUrl + "/zalava/accounts/permissions");
    var read = page.locator("[data-permission-tool='shopping-list-household:list_items']");
    PlaywrightAssertions.assertThat(read).containsText("Default: no confirmation");
    PlaywrightAssertions.assertThat(read).containsText("List items");
    for (int width : new int[] {1440, 768, 390}) {
      page.setViewportSize(width, 900);
      if (width <= 1024)
        page.waitForFunction(
            "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
      assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
          .isTrue();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(diagnostics.resolve("unified-role-" + width + ".png"))
              .setFullPage(true));
    }
    page.setViewportSize(1440, 900);
    String memberId =
        page.locator("#permission-subject option")
            .filter(new Locator.FilterOptions().setHasText("released-clock-member"))
            .getAttribute("value");
    page.locator("#permission-subject").selectOption(memberId);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show permissions"))
        .click();
    PlaywrightAssertions.assertThat(
            page.locator("[data-permission-app='zalava-module-shopping-list:shopping-list']"))
        .containsText("Individual override");
    for (int width : new int[] {1440, 768, 390}) {
      page.setViewportSize(width, 900);
      if (width <= 1024)
        page.waitForFunction(
            "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
      assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
          .isTrue();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(diagnostics.resolve("unified-person-" + width + ".png"))
              .setFullPage(true));
    }
    page.setViewportSize(1440, 900);
    page.locator("#permission-subject").selectOption("administrator");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show permissions"))
        .click();
    read.locator("form[action$='/confirmation'] select").selectOption("REQUIRE");
    read.getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save Chat confirmation"))
        .focus();
    page.keyboard().press("Enter");
    page.getByText("Chat confirmation saved.").waitFor();
    page.reload();
    PlaywrightAssertions.assertThat(read).containsText("Require confirmation");
    sendShoppingToolTurn(
        page, modelServer, "Read-only confirmation required", "list_items", Map.of());
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions"))
        .click();
    var card =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Pending permission"));
    PlaywrightAssertions.assertThat(card).containsText("list_items");
    card.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Allow once")).click();
    page.getByText(
            "Read approved. Ask the same question again in this conversation within five minutes.")
        .waitFor();
    page.locator("#message").fill("Read-only confirmation required");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    PlaywrightAssertions.assertThat(
            page.getByText(
                "Policy tool call completed.", new Page.GetByTextOptions().setExact(true)))
        .hasCount(2);
    PlaywrightAssertions.assertThat(
            page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions")))
        .hasCount(0);
    modelServer.verify(
        WireMock.postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(containing("Read-only confirmation required"))
            .withRequestBody(containing("Policy automatic milk")));
    page.navigate(baseUrl + "/zalava/accounts/permissions?subject=administrator");
    read.locator("form[action$='/confirmation'] select").selectOption("INHERIT");
    read.getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save Chat confirmation"))
        .click();
    sendShoppingToolTurn(page, modelServer, "Read-only default auto", "list_items", Map.of());
    PlaywrightAssertions.assertThat(
            page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions")))
        .hasCount(0);
  }

  private void proveKnowledgeReadConfirmation(Page page, WireMockServer modelServer) {
    String marker = "Knowledge approval test rarecedar";
    String fact = "rarecedar-8392 is the private fixture fact.";
    page.navigate(baseUrl + "/knowledge");
    page.locator("#knowledge-file")
        .setInputFiles(
            new FilePayload(
                "confirmation-note.txt", "text/plain", fact.getBytes(StandardCharsets.UTF_8)));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload").setExact(true))
        .click();
    page.getByText("Source uploaded for processing.").waitFor();
    Locator document =
        page.locator("li.box")
            .filter(new Locator.FilterOptions().setHasText("confirmation-note.txt"));
    for (int attempt = 0; attempt < 100 && !document.innerText().contains("Ready"); attempt++) {
      page.waitForTimeout(300);
      page.reload();
    }
    PlaywrightAssertions.assertThat(document).containsText("Ready");
    page.navigate(baseUrl + "/zalava/accounts/permissions?subject=administrator");
    Locator read = page.locator("[data-permission-tool='zalava-knowledge:knowledge.search']");
    PlaywrightAssertions.assertThat(read).containsText("Governed by document permissions");
    for (int width : new int[] {1440, 768, 390}) {
      page.setViewportSize(width, 900);
      if (width <= 1024)
        page.waitForFunction(
            "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
      assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
          .isTrue();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(diagnostics.resolve("knowledge-permission-" + width + ".png"))
              .setFullPage(true));
    }
    page.setViewportSize(1440, 900);
    read.locator("form[action$='/confirmation'] select").selectOption("REQUIRE");
    read.getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save Chat confirmation"))
        .click();
    page.getByText("Chat confirmation saved.").waitFor();
    String call =
        """
        data: {"id":"knowledge","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"knowledge-call","type":"function","function":{"name":"zalava_knowledge_search_f2405c6159c995e8","arguments":%s}}]},"finish_reason":null}]}

        data: {"id":"knowledge","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

        data: [DONE]

        """
            .formatted(
                JSON.writeValueAsString(
                    JSON.writeValueAsString(Map.of("query", "rarecedar", "limit", 2))));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(2)
            .withRequestBody(
                matchingJsonPath("$.messages[?(@.role == 'user')].content", containing(marker)))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/event-stream").withBody(call)));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(0)
            .withRequestBody(
                matchingJsonPath("$.messages[?(@.role == 'user')].content", containing(marker)))
            .withRequestBody(matchingJsonPath("$.messages[?(@.role == 'tool')]"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        """
        data: {"id":"knowledge-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Knowledge tool call completed."},"finish_reason":null}]}

        data: {"id":"knowledge-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: [DONE]

        """)));
    page.navigate(baseUrl + "/chat");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
        .click();
    page.locator("#message").fill(marker);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    page.getByText("Knowledge tool call completed.").waitFor();
    assertThat(
            modelServer.getAllServeEvents().stream()
                .map(event -> event.getRequest().getBodyAsString())
                .filter(body -> body.contains(marker)))
        .allMatch(body -> !body.contains("rarecedar-8392"));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions"))
        .click();
    var card =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Pending permission"));
    PlaywrightAssertions.assertThat(card).containsText("knowledge.search");
    card.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Allow once")).click();
    page.getByText(
            "Read approved. Ask the same question again in this conversation within five minutes.")
        .waitFor();
    page.locator("#message").fill(marker);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    PlaywrightAssertions.assertThat(page.getByText("Knowledge tool call completed.")).hasCount(2);
    modelServer.verify(
        WireMock.postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(containing(marker))
            .withRequestBody(containing("rarecedar-8392")));
  }

  private void proveHostCallbackConfirmation(Page page, WireMockServer modelServer) {
    String marker = "Host callback approval scenario";
    String fact = "I prefer apricot orchards for acceptance tests.";
    page.navigate(baseUrl + "/zalava/accounts/permissions?subject=administrator");
    var row = page.locator("[data-permission-tool='zalava-internal:proposeMemory']");
    PlaywrightAssertions.assertThat(row).containsText("Propose Memory");
    PlaywrightAssertions.assertThat(row).containsText("Run without confirmation by default");
    page.locator("#permission-filter").fill("propose memory");
    PlaywrightAssertions.assertThat(row).isVisible();
    PlaywrightAssertions.assertThat(
            page.locator("[data-permission-tool='zalava-internal:beginModuleDevelopment']"))
        .isHidden();
    PlaywrightAssertions.assertThat(page.locator("[data-permission-filter-status]"))
        .containsText("1 matching");
    page.locator("#permission-filter").fill("no such permission");
    PlaywrightAssertions.assertThat(page.locator("[data-permission-filter-status]"))
        .containsText("0 matching");
    page.locator("#permission-filter").fill("");
    PlaywrightAssertions.assertThat(row).isVisible();
    for (int width : new int[] {1440, 768, 390}) {
      page.setViewportSize(width, 900);
      if (width <= 1024)
        page.waitForFunction(
            "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
      assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
          .isTrue();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(diagnostics.resolve("host-callback-" + width + ".png"))
              .setFullPage(true));
    }
    page.setViewportSize(1440, 900);
    row.locator("form[action$='/confirmation'] select").selectOption("REQUIRE");
    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save Chat confirmation"))
        .click();
    page.getByText("Chat confirmation saved.").waitFor();
    Map<String, Object> proposal = Map.of("scope", "USER", "text", fact, "metadata", Map.of());
    String call =
        """
        data: {"id":"host","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"host-call","type":"function","function":{"name":"proposeMemory","arguments":%s}}]},"finish_reason":null}]}

        data: {"id":"host","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

        data: [DONE]

        """
            .formatted(
                JSON.writeValueAsString(JSON.writeValueAsString(Map.of("proposal", proposal))));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(2)
            .withRequestBody(
                matchingJsonPath("$.messages[?(@.role == 'user')].content", containing(marker)))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/event-stream").withBody(call)));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(0)
            .withRequestBody(
                matchingJsonPath("$.messages[?(@.role == 'user')].content", containing(marker)))
            .withRequestBody(matchingJsonPath("$.messages[?(@.role == 'tool')]"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        """
        data: {"id":"host-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Host callback completed."},"finish_reason":null}]}

        data: {"id":"host-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: [DONE]

        """)));
    page.navigate(baseUrl + "/chat");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
        .click();
    page.locator("#message").fill(marker);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    page.getByText("Host callback completed.").waitFor();
    assertThat(page.context().request().get(baseUrl + "/memory").text()).doesNotContain(fact);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions"))
        .click();
    var card =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Pending permission"));
    PlaywrightAssertions.assertThat(card).containsText("proposeMemory");
    card.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Allow once")).click();
    page.getByText(
            "Action approved. Ask the same question again in this conversation within five minutes.")
        .waitFor();
    page.locator("#message").fill(marker);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    PlaywrightAssertions.assertThat(page.getByText("Host callback completed.")).hasCount(2);
    assertThat(page.context().request().get(baseUrl + "/memory").text()).contains(fact);

    String deniedMarker = "Host callback denial scenario";
    String deniedFact = "I prefer denied plum orchards for acceptance tests.";
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(2)
            .withRequestBody(
                matchingJsonPath(
                    "$.messages[?(@.role == 'user')].content", containing(deniedMarker)))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(call.replace(fact, deniedFact))));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(0)
            .withRequestBody(
                matchingJsonPath(
                    "$.messages[?(@.role == 'user')].content", containing(deniedMarker)))
            .withRequestBody(matchingJsonPath("$.messages[?(@.role == 'tool')]"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        """
        data: {"id":"denied-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Denied callback completed."},"finish_reason":null}]}

        data: {"id":"denied-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: [DONE]

        """)));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
        .click();
    page.locator("#message").fill(deniedMarker);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    page.getByText("Denied callback completed.").waitFor();
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Review permissions"))
        .click();
    var deniedCard =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Pending permission"));
    PlaywrightAssertions.assertThat(deniedCard).containsText("proposeMemory");
    deniedCard.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Deny")).click();
    page.getByText("Permission denied; operation was not run.").waitFor();
    assertThat(page.context().request().get(baseUrl + "/memory").text()).doesNotContain(deniedFact);
  }

  private void sendShoppingPolicyTurn(
      Page page, WireMockServer modelServer, String marker, String item) {
    sendShoppingToolTurn(page, modelServer, marker, "add_item", Map.of("name", item));
  }

  private void sendShoppingToolTurn(
      Page page,
      WireMockServer modelServer,
      String marker,
      String tool,
      Map<String, Object> arguments) {
    String callback = ZalavaToolCallbackNames.forTool("shopping-list-household", tool);
    String call =
        """
        data: {"id":"policy","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"policy-call","type":"function","function":{"name":"%s","arguments":%s}}]},"finish_reason":null}]}

        data: {"id":"policy","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

        data: [DONE]

        """
            .formatted(callback, JSON.writeValueAsString(JSON.writeValueAsString(arguments)));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(2)
            .withRequestBody(
                matchingJsonPath("$.messages[?(@.role == 'user')].content", containing(marker)))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/event-stream").withBody(call)));
    modelServer.stubFor(
        WireMock.post(urlEqualTo("/v1/chat/completions"))
            .atPriority(0)
            .withRequestBody(
                matchingJsonPath("$.messages[?(@.role == 'user')].content", containing(marker)))
            .withRequestBody(matchingJsonPath("$.messages[?(@.role == 'tool')]"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        """
        data: {"id":"policy-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Policy tool call completed."},"finish_reason":null}]}

        data: {"id":"policy-result","object":"chat.completion.chunk","created":1,"model":"fixture-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: [DONE]

        """)));
    page.navigate(baseUrl + "/chat");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
        .click();
    page.locator("#message").fill(marker);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    page.getByText("Policy tool call completed.", new Page.GetByTextOptions().setExact(true))
        .waitFor();
  }

  private void start(PostgreSQLContainer postgres, Path workspace, String profile)
      throws Exception {
    start(postgres, workspace, profile, false);
  }

  private void start(
      PostgreSQLContainer postgres, Path workspace, String profile, boolean jobsEnabled)
      throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    baseUrl = "http://127.0.0.1:" + port;
    Path jar =
        Path.of(System.getProperty("zalava.test.boot-jar", "unused-oci.jar")).toAbsolutePath();
    if (ociImage == null) assertThat(jar).exists();
    Path log = diagnostics.resolve("host-" + ++generation + "-" + profile + ".log");
    process =
        hostProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Xmx768m",
                "-Dhttps.proxyHost=127.0.0.1",
                "-Dhttps.proxyPort=" + (catalogProxy == null ? 0 : catalogProxy.port()),
                "-Djavax.net.ssl.trustStore="
                    + (catalogProxy == null ? "" : catalogProxy.trustStore()),
                "-Djavax.net.ssl.trustStorePassword=fixture",
                "-Dzalava.module.shopping-list.sqlite.path="
                    + workspace.resolve("shopping-list.sqlite"),
                "-jar",
                jar.toString(),
                "--spring.profiles.active=" + profile,
                "--server.address=127.0.0.1",
                "--server.port=" + port,
                "--management.server.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--agent.workspace=" + workspace.toUri(),
                "--zalava.module-configuration.root=" + workspace.resolve("configuration"),
                "--zalava.accounts.security-enabled=true",
                "--zalava.accounts.bootstrap-login=" + LOGIN,
                "--zalava.accounts.bootstrap-password=" + INITIAL_PASSWORD,
                "--agent.onboarding.completed=true",
                "--spring.ai.model.chat=unknown",
                "--agent.channels.telegram.token=false",
                "--agent.channels.telegram.username=false",
                "--jobrunr.background-job-server.enabled=" + jobsEnabled,
                "--jobrunr.dashboard.enabled=false")
            .directory(workspace.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
    while (System.nanoTime() < deadline) {
      assertThat(process.isAlive()).as("packaged host startup; diagnostics: %s", log).isTrue();
      try {
        if (http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode()
            == 200) {
          if (ociImage != null) {
            assertThat(Files.readString(log))
                .as("usable AOT cache in image; diagnostics: %s", log)
                .doesNotContain(
                    "Unable to map shared spaces",
                    "An error has occurred while processing the AOT cache",
                    "size has changed");
          }
          return;
        }
      } catch (IOException ignored) {
        // The packaged server has not bound its random loopback port yet.
      }
      Thread.sleep(250);
    }
    throw new AssertionError("Packaged host startup timed out; diagnostics: " + log);
  }

  private String fixtureUser() {
    try {
      return Files.getAttribute(diagnostics, "unix:uid")
          + ":"
          + Files.getAttribute(diagnostics, "unix:gid");
    } catch (IOException failure) {
      throw new IllegalStateException(
          "OCI acceptance requires Linux bind-mount ownership", failure);
    }
  }

  private void stop() throws Exception {
    if (process != null) {
      if (ociComposeFleet != null) {
        ociComposeFleet.stop();
        ociComposeFleet = null;
      }
      if (ociContainer != null) {
        Process stopping =
            new ProcessBuilder("docker", "stop", "--time", "20", ociContainer)
                .redirectErrorStream(true)
                .redirectOutput(diagnostics.resolve("docker-stop-" + generation + ".log").toFile())
                .start();
        assertThat(stopping.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(stopping.exitValue()).isZero();
        ociContainer = null;
      }
      process.destroy();
      if (!process.waitFor(20, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
      }
      process = null;
    }
  }

  private void signIn(Page page, String login, String password, boolean temporary) {
    page.navigate(baseUrl + "/login");
    page.locator("input[name=username]").fill(login);
    page.locator("input[name=password]").fill(password);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    if (temporary) {
      page.locator("input[name=currentPassword]").waitFor();
      page.locator("input[name=currentPassword]").fill(password);
      page.locator("input[name=replacementPassword]").fill(password + "-changed");
      page.locator("input[name=passwordConfirmation]").fill(password + "-changed");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
          .click();
    }
    page.waitForURL(url -> !url.endsWith("/login") && !url.endsWith("/account/password"));
  }

  private void installFromCatalog(Page page, BrowserContext admin, Release release)
      throws Exception {
    catalogProxy.invalidMetadata();
    page.navigate(baseUrl + "/modules");
    page.locator("[data-action=refresh-catalog]").click();
    page.getByRole(AriaRole.ALERT).waitFor();
    assertThat(page.locator("[data-installation-request]").count()).isZero();
    catalogProxy.restoreMetadata();
    page.navigate(baseUrl + "/modules");
    page.locator("[data-action=refresh-catalog]").click();
    assertThat(page.locator("main").textContent()).contains("Catalog refreshed: 1 module(s)");
    page.navigate(baseUrl + "/modules/" + release.moduleId());
    page.locator("[data-release-version]").selectOption(release.version());
    catalogProxy.corruptArtifact();
    page.locator("[data-action=request-installation]").click();
    page.getByRole(AriaRole.ALERT).waitFor();
    page.navigate(baseUrl + "/modules");
    assertThat(page.locator("[data-installation-request]").count()).isZero();
    catalogProxy.artifact(release.path());
    prepareCatalogRelease(page, release);
    page.locator("[data-installation-request] [data-action=deny]").click();
    page.navigate(baseUrl + "/monitoring#module-installations");
    assertThat(page.locator("[data-installation-history]").textContent())
        .containsIgnoringCase("denied");
    prepareCatalogRelease(page, release);
    page.locator("[data-installation-request] [data-action=allow]").click();
    page.navigate(baseUrl + "/monitoring#module-installations");
    page.locator("[data-installation-history]")
        .filter(new Locator.FilterOptions().setHasText("SUCCEEDED"))
        .waitFor();
    page.screenshot(
        new Page.ScreenshotOptions().setPath(diagnostics.resolve("catalog-installation.png")));
  }

  private void prepareCatalogRelease(Page page, Release release) {
    page.navigate(baseUrl + "/zalava/control");
    waitForControlSwap(
        page,
        "/catalog/refresh",
        () ->
            page.locator("#module-release-installations button[hx-post$='/catalog/refresh']")
                .click());
    if (!page.locator("#release-module-id").inputValue().equals(release.moduleId())) {
      waitForControlSwap(
          page,
          "/catalog/select",
          () -> page.locator("#release-module-id").selectOption(release.moduleId()));
    }
    page.locator("#release-version").selectOption(release.version());
    page.locator("#module-release-installations button[type=submit]").click();
    page.locator("#module-release-installations button[hx-post$='/allow']").waitFor();
    page.navigate(baseUrl + "/modules");
    page.locator("[data-installation-request] [data-action=allow]").waitFor();
  }

  private void waitForControlSwap(Page page, String path, Runnable action) {
    page.evaluate(
        """
        () => {
          delete document.documentElement.dataset.controlSettled;
          document.addEventListener('htmx:afterSettle', () => {
            document.documentElement.dataset.controlSettled = 'true';
          }, {once: true});
        }
        """);
    var response = page.waitForResponse(candidate -> candidate.url().endsWith(path), action);
    assertThat(response.status()).isEqualTo(200);
    response.finished();
    page.waitForFunction("() => document.documentElement.dataset.controlSettled === 'true'");
  }

  private void startModule(Page page, String moduleId) {
    page.navigate(baseUrl + "/modules/" + moduleId);
    var start = page.locator("form[action='/modules/" + moduleId + "/start'] button");
    if (start.count() > 0) start.click();
  }

  private APIResponse invoke(
      BrowserContext context,
      Page page,
      String provider,
      String tool,
      Map<String, Object> arguments) {
    return post(
        context,
        page,
        "/api/zalava/providers/" + provider + "/tools/" + tool + "/invoke",
        Map.of("actorId", LOGIN, "confirmed", false, "arguments", arguments));
  }

  private APIResponse post(
      BrowserContext context, Page page, String path, Map<String, Object> body) {
    page.navigate(baseUrl + "/modules");
    String csrf =
        context.cookies(baseUrl).stream()
            .filter(cookie -> cookie.name.equals("XSRF-TOKEN"))
            .findFirst()
            .orElseThrow()
            .value;
    return context
        .request()
        .post(
            baseUrl + path, RequestOptions.create().setHeader("X-XSRF-TOKEN", csrf).setData(body));
  }

  private record Release(String moduleId, String version, Path path) {}
}
