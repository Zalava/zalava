package org.zalava.platform.configuration.application.port.out;

import java.util.Map;

public interface ConfigurationChangePublisher {

  void publishConfigurationChanged(Map<String, Object> configuration);
}
