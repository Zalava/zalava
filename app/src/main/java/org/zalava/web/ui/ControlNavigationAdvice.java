package org.zalava.web.ui;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes whether a metrics viewer is configured to the Zalava Control templates. */
@ControllerAdvice(assignableTypes = ZalavaControlUiController.class)
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
