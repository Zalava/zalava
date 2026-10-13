package org.zalava.assistant.models.configuration.application.port.in;

/** Internal runtime configuration query. Credential values must never be rendered or logged. */
public interface ModelProviderState {
  String property(String name, String fallback);
}
