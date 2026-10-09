package org.zalava.assistant.chat.api;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.zalava.SupportedProvider;

/** Configured provider availability is distinct from WebSocket connectivity. */
@Component
public final class ChatProviderReadiness {
  private final Environment environment;

  public ChatProviderReadiness(Environment environment) {
    this.environment = environment;
  }

  public boolean configured() {
    return SupportedProvider.from(environment.getProperty("spring.ai.model.chat", "unknown"))
        .isPresent();
  }
}
