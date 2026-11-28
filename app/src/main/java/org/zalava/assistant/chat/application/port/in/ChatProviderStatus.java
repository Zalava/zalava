package org.zalava.assistant.chat.application.port.in;

/** Reports whether a supported chat provider is selected, not whether it is healthy. */
public interface ChatProviderStatus {
  boolean configured();
}
