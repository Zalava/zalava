package org.zalava.configuration.application;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.zalava.configuration.application.port.in.ConfigurationManagement;
import org.zalava.configuration.application.port.out.ConfigurationChangePublisher;
import org.zalava.configuration.application.port.out.ConfigurationStore;

public final class DefaultConfigurationManagement implements ConfigurationManagement {

  private final ConfigurationStore configurationStore;
  private final ConfigurationChangePublisher changePublisher;

  public DefaultConfigurationManagement(
      ConfigurationStore configurationStore, ConfigurationChangePublisher changePublisher) {
    this.configurationStore = configurationStore;
    this.changePublisher = changePublisher;
  }

  @Override
  public void updateProperty(String key, Object value) throws IOException {
    updateProperties(Map.of(key, value));
  }

  @Override
  public void updateProperties(Map<String, Object> keyValues) throws IOException {
    Map<String, Object> configuration = readApplicationYaml();
    keyValues.forEach((key, value) -> setNestedValue(configuration, key.split("\\."), value));
    configurationStore.write(configuration);
    changePublisher.publishConfigurationChanged(configuration);
  }

  @Override
  public Map<String, Object> readApplicationYaml() throws IOException {
    return new LinkedHashMap<>(configurationStore.read());
  }

  @SuppressWarnings("unchecked")
  private void setNestedValue(Map<String, Object> map, String[] keys, Object value) {
    for (int index = 0; index < keys.length - 1; index++) {
      map =
          (Map<String, Object>) map.computeIfAbsent(keys[index], ignored -> new LinkedHashMap<>());
    }
    map.put(keys[keys.length - 1], value);
  }
}
