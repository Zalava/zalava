package org.zalava.identity.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.support.SeaComponentTestConfiguration;
import org.zalava.support.SeaComponentTestInitializer;

/**
 * Regression for the managed metrics stack: Prometheus scrapes the loopback management server
 * without a SEA session, while the public application port keeps its authorization.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = SeaComponentTestInitializer.class)
@Import(SeaComponentTestConfiguration.class)
class ManagementMetricsEndpointComponentTest {

  private static final HttpClient CLIENT =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @LocalServerPort private int serverPort;

  @LocalManagementPort private int managementPort;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> "management-metrics-admin");
    registry.add("sea.accounts.bootstrap-password", () -> "ManagementMetricsPassword-123");
    registry.add("sea.observability.enabled", () -> "true");
    registry.add("management.server.port", () -> "0");
  }

  @Test
  void managementPrometheusEndpointIsScrapeableWithoutAuthentication() throws Exception {
    HttpResponse<String> management = get(managementPort, "/actuator/prometheus");

    assertThat(management.statusCode()).isEqualTo(200);
    assertThat(management.body()).contains("# HELP");

    HttpResponse<String> application = get(serverPort, "/actuator/prometheus");

    assertThat(application.statusCode()).isNotEqualTo(200);
  }

  private static HttpResponse<String> get(int port, String path) throws Exception {
    return CLIENT.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
        BodyHandlers.ofString());
  }
}
