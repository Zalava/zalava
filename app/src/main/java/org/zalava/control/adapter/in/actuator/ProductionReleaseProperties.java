package org.zalava.control.adapter.in.actuator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sea.release")
public record ProductionReleaseProperties(String image, String revision) {

  public ProductionReleaseProperties {
    image = normalize(image);
    revision = normalize(revision);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }
}
