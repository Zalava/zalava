package org.zalava.platform.configuration.application.port.out;

import java.io.IOException;
import java.util.Map;

public interface ConfigurationStore {

  Map<String, Object> read() throws IOException;

  void write(Map<String, Object> configuration) throws IOException;
}
