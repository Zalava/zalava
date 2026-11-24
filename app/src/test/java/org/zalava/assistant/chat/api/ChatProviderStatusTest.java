package org.zalava.assistant.chat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.zalava.assistant.chat.application.port.in.ChatProviderStatus;
import org.zalava.assistant.models.configuration.application.port.in.ModelProviderState;

class ChatProviderStatusTest {
  @Test
  void reportsCurrentSupportedSelectionWithoutClaimingConnectivity() {
    var state = mock(ModelProviderState.class);
    when(state.property("spring.ai.model.chat", "unknown")).thenReturn("unknown", "ollama");
    ChatProviderStatus status = new ChatProviderReadiness(state);
    assertThat(status.configured()).isFalse();
    assertThat(status.configured()).isTrue();
  }
}
