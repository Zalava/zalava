package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
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
            .check();
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
    Properties pins = new Properties();
    try (var input = getClass().getResourceAsStream("/released-modules.properties")) {
      assertThat(input).as("immutable released-module pins").isNotNull();
      pins.load(input);
    }
    assertThat(pins).hasSize(12);
    List<Release> result = new ArrayList<>();
    for (String name : pins.stringPropertyNames().stream().sorted().toList()) {
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

  private void start(PostgreSQLContainer postgres, Path workspace, String profile)
      throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    baseUrl = "http://127.0.0.1:" + port;
    Path jar = Path.of(System.getProperty("zalava.test.boot-jar")).toAbsolutePath();
    assertThat(jar).exists();
    Path log = diagnostics.resolve("host-" + ++generation + "-" + profile + ".log");
    process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Xmx768m",
                "-Dhttps.proxyHost=127.0.0.1",
                "-Dhttps.proxyPort=" + catalogProxy.port(),
                "-Djavax.net.ssl.trustStore=" + catalogProxy.trustStore(),
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
                "--jobrunr.background-job-server.enabled=false",
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
            == 200) return;
      } catch (IOException ignored) {
        // The packaged server has not bound its random loopback port yet.
      }
      Thread.sleep(250);
    }
    throw new AssertionError("Packaged host startup timed out; diagnostics: " + log);
  }

  private void stop() throws Exception {
    if (process != null) {
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
    assertThat(page.locator("[data-request-status]").textContent()).containsIgnoringCase("denied");
    prepareCatalogRelease(page, release);
    page.locator("[data-installation-request] [data-action=allow]").click();
    page.locator("[data-request-status]")
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
