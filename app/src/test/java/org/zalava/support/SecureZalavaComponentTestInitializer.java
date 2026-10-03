package org.zalava.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

final class SecureZalavaComponentTestInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override
  public void initialize(ConfigurableApplicationContext context) {
    ZalavaComponentTestInitializer.initialize(context, true);
  }
}
