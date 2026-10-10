package org.zalava.assistant.chat.api;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog;

/** Configured provider availability is distinct from WebSocket connectivity. */
@Component
public final class ChatProviderReadiness {
  private final Environment environment;

  public ChatProviderReadiness(Environment environment) {
    this.environment = environment;
  }

  public boolean configured() {
    return ChatProviderCatalog.supportsRuntime(
        environment.getProperty("spring.ai.model.chat", "unknown"));
  }
}
