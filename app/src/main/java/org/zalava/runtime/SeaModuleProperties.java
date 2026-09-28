package org.zalava.runtime;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Host-owned configuration exposed to module factories under {@code
 * sea.modules.<module-id>.factories.<factory-id>}.
 */
@ConfigurationProperties("sea")
public record SeaModuleProperties(Map<String, Object> modules) {

  public SeaModuleProperties {
    modules = modules == null ? Map.of() : Map.copyOf(modules);
  }
}
