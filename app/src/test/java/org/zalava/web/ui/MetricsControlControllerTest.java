package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.ModelAndView;

class MetricsControlControllerTest {

  @Test
  void redirectsManagedModeToTheLoopbackGrafanaViewer() {
    var response = redirect("managed", "");

    assertThat(response.getStatusCode().value()).isEqualTo(302);
    assertThat(response.getHeaders().getLocation()).hasToString("http://127.0.0.1:3000");
  }

  @Test
  void redirectsExternalModeOnlyToTheConfiguredUrl() {
    var response = redirect("external", "https://metrics.example");

    assertThat(response.getStatusCode().value()).isEqualTo(302);
    assertThat(response.getHeaders().getLocation()).hasToString("https://metrics.example");
  }

  @Test
  void rendersGuidanceWhenDisabled() {
    var view =
        (ModelAndView)
            new MetricsControlController(new ObservabilitySettings("disabled", "")).metrics();

    assertThat(view.getViewName()).isEqualTo("zalava/control/metrics");
    assertThat(view.getModel()).containsEntry("metricsEnabled", false);
  }

  @Test
  void rendersGuidanceForANonHttpsExternalViewerUrl() {
    var view =
        (ModelAndView)
            new MetricsControlController(
                    new ObservabilitySettings("external", "http://metrics.example"))
                .metrics();

    assertThat(view.getViewName()).isEqualTo("zalava/control/metrics");
  }

  @Test
  void rendersGuidanceForAMalformedExternalViewerUrl() {
    var view =
        (ModelAndView)
            new MetricsControlController(
                    new ObservabilitySettings("external", "https://metrics example"))
                .metrics();

    assertThat(view.getViewName()).isEqualTo("zalava/control/metrics");
  }

  private static ResponseEntity<?> redirect(String mode, String externalUrl) {
    return (ResponseEntity<?>)
        new MetricsControlController(new ObservabilitySettings(mode, externalUrl)).metrics();
  }
}
