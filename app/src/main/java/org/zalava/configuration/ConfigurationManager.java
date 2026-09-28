package org.zalava.configuration;

import java.io.IOException;
import java.util.Map;
import org.zalava.configuration.application.port.in.ConfigurationManagement;

/**
 * Compatibility facade for callers that have not yet moved to the configuration application ports.
 */
@Deprecated(forRemoval = false)
public class ConfigurationManager implements ConfigurationManagement {

  private final ConfigurationManagement configurationManagement;

  public ConfigurationManager(ConfigurationManagement configurationManagement) {
    this.configurationManagement = configurationManagement;
  }

  public void updateProperty(String key, Object value) throws IOException {
    configurationManagement.updateProperty(key, value);
  }

  public void updateProperties(Map<String, Object> keyValues) throws IOException {
    configurationManagement.updateProperties(keyValues);
  }

  public Map<String, Object> readApplicationYaml() throws IOException {
    return configurationManagement.readApplicationYaml();
  }
}
