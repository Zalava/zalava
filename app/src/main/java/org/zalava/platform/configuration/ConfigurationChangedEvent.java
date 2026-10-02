package org.zalava.platform.configuration;

import java.util.LinkedHashMap;
import java.util.Map;

public record ConfigurationChangedEvent(Map<String, Object> allConfig) {

  public Object getConfiguration(String key) {
    String[] keys = key.split("\\.");
    Map<String, Object> map = allConfig;
    for (int i = 0; i < keys.length - 1; i++) {
      Object configItem = map.get(keys[i]);
      if (configItem == null) return null;
      else if (configItem instanceof Map<?, ?> nestedMap) {
        Map<String, Object> nestedConfiguration = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
          if (!(entry.getKey() instanceof String nestedKey)) {
            return null;
          }
          nestedConfiguration.put(nestedKey, entry.getValue());
        }
        map = nestedConfiguration;
      }
    }
    return map.get(keys[keys.length - 1]);
  }
}
