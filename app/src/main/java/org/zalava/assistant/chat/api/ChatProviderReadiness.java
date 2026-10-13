package org.zalava.assistant.chat.api;

import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.zalava.assistant.models.configuration.application.port.in.ModelProviderState;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog;

/** Configured provider availability is distinct from WebSocket connectivity. */
@Component
public final class ChatProviderReadiness {
  private final BiFunction<String, String, String> properties;

  public ChatProviderReadiness(Environment environment) {
    this.properties = environment::getProperty;
  }

  @Autowired
  public ChatProviderReadiness(ModelProviderState state) {
    this.properties = state::property;
  }

  public boolean configured() {
    return ChatProviderCatalog.supportsRuntime(properties.apply("spring.ai.model.chat", "unknown"));
  }
}
