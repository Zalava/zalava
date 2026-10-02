package org.zalava.platform.configuration.application.port.in;

import java.io.IOException;
import java.util.Map;

public interface ConfigurationQueries {

  Map<String, Object> readApplicationYaml() throws IOException;
}
