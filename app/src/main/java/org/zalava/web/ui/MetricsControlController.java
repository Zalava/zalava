package org.zalava.web.ui;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

/** Administrator-only boundary for the separately hosted metrics viewer. */
@Controller
class MetricsControlController {

  private final ObservabilitySettings observability;

  MetricsControlController(ObservabilitySettings observability) {
    this.observability = observability;
  }

  @GetMapping(ZalavaControlUiController.PATH + "/metrics")
  Object metrics() {
    String target = observability.viewerUrl();
    if (target == null) {
      return new ModelAndView("zalava/control/metrics").addObject("metricsEnabled", false);
    }
    return ResponseEntity.status(302)
        .header(HttpHeaders.LOCATION, URI.create(target).toString())
        .build();
  }
}
