package org.zalava.platform.configuration.adapter.out.spring;

import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.zalava.platform.configuration.ConfigurationChangedEvent;
import org.zalava.platform.configuration.application.port.out.ConfigurationChangePublisher;

public final class SpringConfigurationChangePublisher implements ConfigurationChangePublisher {

  private final ApplicationEventPublisher eventPublisher;

  public SpringConfigurationChangePublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void publishConfigurationChanged(Map<String, Object> configuration) {
    eventPublisher.publishEvent(new ConfigurationChangedEvent(configuration));
  }
}
