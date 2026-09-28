package org.zalava.configuration.application.port.in;

import java.io.IOException;
import java.util.Map;

public interface ConfigurationCommands {

  void updateProperty(String key, Object value) throws IOException;

  void updateProperties(Map<String, Object> keyValues) throws IOException;
}
