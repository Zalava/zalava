package org.zalava.catalog.install.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.support.PostgreSqlTestDatabase;

/**
 * Opt-in real-network security negative: a private GitHub Packages release cannot be prepared
 * without artifact credentials. The locator token is present so the failure is attributable to the
 * missing package credentials, not to catalog access.
 */
@Tag("indexed-install")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IndexedInstallPrivatePackagesAuthNegativeTest {

  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private static final Path WORKSPACE = createWorkspace();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("sea.accounts.security-enabled", () -> "false");
    registry.add("sea.accounts.bootstrap-login", () -> "indexed-install-noauth-admin");
    registry.add("sea.accounts.bootstrap-password", () -> "IndexedInstallPassword-123");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("sea.catalog.github.token", IndexedInstallCredentials::githubToken);
    PostgreSqlTestDatabase.register(registry);
  }

  @Test
  void missingPrivatePackageCredentialsSurfaceAsARejectedPrepareWithoutEnablement()
      throws Exception {
    assertThat(IndexedInstallCredentials.githubToken())
        .as("the locator token is required to reach the private catalog")
        .isNotBlank();

    post("/sea/control/module-release-installations/catalog/refresh");
    post(
        "/sea/control/module-release-installations/catalog/select",
        Map.of("moduleId", IndexedInstallAcceptanceTest.MODULE_ID));

    String response =
        post(
            "/sea/control/module-release-installations",
            Map.of(
                "moduleId",
                IndexedInstallAcceptanceTest.MODULE_ID,
                "version",
                IndexedInstallAcceptanceTest.VERSION));

    assertThat(response).contains("Curated Maven checksum request failed: HTTP 401");
    assertThat(Files.exists(WORKSPACE.resolve("source-module-installation/enabled-modules.json")))
        .isFalse();
    Path requests = WORKSPACE.resolve("source-module-installation/release-requests");
    if (Files.isDirectory(requests)) {
      try (var paths = Files.list(requests)) {
        assertThat(paths.filter(path -> path.getFileName().toString().endsWith(".json"))).isEmpty();
      }
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
            .timeout(Duration.ofMinutes(2))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-indexed-install-noauth-");
      Files.writeString(workspace.resolve("AGENT.md"), "Indexed install negative workspace.");
      Files.writeString(workspace.resolve("INFO.md"), "Disposable real-network workspace.");
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
