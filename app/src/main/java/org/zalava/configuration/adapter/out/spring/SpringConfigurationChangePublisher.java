package org.zalava.configuration.adapter.out.spring;

import java.util.Map;
import org.zalava.configuration.ConfigurationChangedEvent;
import org.zalava.configuration.application.port.out.ConfigurationChangePublisher;
import org.springframework.context.ApplicationEventPublisher;

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
