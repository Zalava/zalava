package org.zalava.ui;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes whether a metrics viewer is configured to the SEA Control templates. */
@ControllerAdvice(assignableTypes = SeaControlUiController.class)
final class ControlNavigationAdvice {

  private final ObservabilitySettings observability;

  ControlNavigationAdvice(ObservabilitySettings observability) {
    this.observability = observability;
  }

  @ModelAttribute("metricsEnabled")
  boolean metricsEnabled() {
    return observability.viewerConfigured();
  }
}
