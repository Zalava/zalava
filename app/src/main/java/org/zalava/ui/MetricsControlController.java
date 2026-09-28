package org.zalava.ui;

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

  @GetMapping(SeaControlUiController.PATH + "/metrics")
  Object metrics() {
    String target = observability.viewerUrl();
    if (target == null) {
      return new ModelAndView("sea/control/metrics").addObject("metricsEnabled", false);
    }
    return ResponseEntity.status(302)
        .header(HttpHeaders.LOCATION, URI.create(target).toString())
        .build();
  }
}
