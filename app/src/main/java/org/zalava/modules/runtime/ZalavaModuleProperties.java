package org.zalava.modules.runtime;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Host-owned configuration exposed to module factories under {@code
 * zalava.modules.<module-id>.factories.<factory-id>}.
 */
@ConfigurationProperties("zalava")
public record ZalavaModuleProperties(Map<String, Object> modules) {

  public ZalavaModuleProperties {
    modules = modules == null ? Map.of() : Map.copyOf(modules);
  }
}
