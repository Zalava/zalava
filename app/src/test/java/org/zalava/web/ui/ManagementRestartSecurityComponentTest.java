package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ModulesControllerComponentTest.ChatModelTestConfiguration.class)
class ManagementRestartSecurityComponentTest {
  @Autowired Environment environment;
  @MockitoBean RestartEndpoint endpoint;
  private static final Path WORKSPACE = workspace();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    r.add("management.server.port", () -> "0");
    r.add(
        "management.endpoints.web.exposure.include",
        () -> "health,info,prometheus,metrics,restart");
    r.add("zalava.accounts.security-enabled", () -> "true");
    r.add("zalava.accounts.bootstrap-login", () -> "management-restart-admin");
    r.add("zalava.accounts.bootstrap-password", () -> "RestartPassword-123");
    r.add("spring.ai.model.chat", () -> "none");
    r.add("jobrunr.background-job-server.enabled", () -> "false");
    r.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void separateManagementListenerRetainsScrapesButRejectsAnonymousRestart() throws Exception {
    int port = environment.getRequiredProperty("local.management.port", Integer.class);
    assertThat(port)
        .isNotEqualTo(environment.getRequiredProperty("local.server.port", Integer.class));
    var client = HttpClient.newHttpClient();
    String base = "http://127.0.0.1:" + port + "/actuator/";
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "health")).GET().build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode())
        .isEqualTo(200);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "metrics")).GET().build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode())
        .isEqualTo(200);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "restart")).GET().build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode())
        .isEqualTo(403);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "restart"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode())
        .isEqualTo(403);
    verify(endpoint, never()).restart();
  }

  private static Path workspace() {
    try {
      var p = Files.createTempDirectory("management-restart-component-");
      Files.writeString(p.resolve("AGENT.md"), "Disposable management policy test.");
      return p;
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }
}
