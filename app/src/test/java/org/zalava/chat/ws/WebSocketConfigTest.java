package org.zalava.chat.ws;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

class WebSocketConfigTest {
  @Test
  void retainsSpringsSameOriginDefaultWhenNoOriginsAreConfigured() {
    ChatWebSocketHandler handler = mock(ChatWebSocketHandler.class);
    WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
    WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
    when(registry.addHandler(handler, "/ws/chat")).thenReturn(registration);

    new WebSocketConfig(handler, "").registerWebSocketHandlers(registry);

    verify(registration, never()).setAllowedOrigins("*");
    verify(registration, never()).setAllowedOrigins(new String[0]);
  }

  @Test
  void usesOnlyExplicitConfiguredOrigins() {
    ChatWebSocketHandler handler = mock(ChatWebSocketHandler.class);
    WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
    WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
    when(registry.addHandler(handler, "/ws/chat")).thenReturn(registration);

    new WebSocketConfig(handler, "https://sea.example, https://localhost:8080")
        .registerWebSocketHandlers(registry);

    verify(registration).setAllowedOrigins("https://sea.example", "https://localhost:8080");
  }

  @Test
  void rejectsNonPositiveAttachmentBound() {
    WebSocketConfig config = new WebSocketConfig(mock(ChatWebSocketHandler.class), "");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> config.createWebSocketContainer(0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
