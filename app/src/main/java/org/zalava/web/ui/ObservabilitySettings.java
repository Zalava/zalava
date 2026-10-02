package org.zalava.web.ui;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolved metrics viewer settings. The SEA Control navigation and the metrics redirect boundary
 * both read the same validated value, so a link is only shown when it can actually resolve.
 */
@Component
public class ObservabilitySettings {

  private final String mode;
  private final String externalGrafanaUrl;

  ObservabilitySettings(
      @Value("${sea.observability.mode:disabled}") String mode,
      @Value("${sea.observability.external-grafana-url:}") String externalGrafanaUrl) {
    this.mode = mode;
    this.externalGrafanaUrl = externalGrafanaUrl;
  }

  public boolean viewerConfigured() {
    return viewerUrl() != null;
  }

  public String viewerUrl() {
    return switch (mode) {
      case "managed" -> "http://127.0.0.1:3000";
      case "external" -> validatedExternalGrafanaUrl();
      default -> null;
    };
  }

  private String validatedExternalGrafanaUrl() {
    try {
      URI target = URI.create(externalGrafanaUrl);
      return "https".equals(target.getScheme()) && target.getHost() != null
          ? externalGrafanaUrl
          : null;
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
