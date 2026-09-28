package org.zalava.chat.ws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(
    name = "sea.chat.transport",
    havingValue = "spring-websocket",
    matchIfMissing = true)
public class WebSocketConfig implements WebSocketConfigurer {

  private final ChatWebSocketHandler handler;
  private final UiChatWebSocketHandler uiHandler;
  private final String[] allowedOrigins;

  @Autowired
  public WebSocketConfig(
      ChatWebSocketHandler handler,
      UiChatWebSocketHandler uiHandler,
      @Value("${sea.chat.allowed-origins:}") String allowedOrigins) {
    this.handler = handler;
    this.uiHandler = uiHandler;
    this.allowedOrigins = parseOrigins(allowedOrigins);
  }

  WebSocketConfig(ChatWebSocketHandler handler, String allowedOrigins) {
    this.handler = handler;
    this.uiHandler = null;
    this.allowedOrigins = parseOrigins(allowedOrigins);
  }

  private static String[] parseOrigins(String allowedOrigins) {
    return allowedOrigins.isBlank()
        ? new String[0]
        : java.util.Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toArray(String[]::new);
  }

  @Bean
  @ConditionalOnProperty(
      name = "sea.chat.attachment.bind-container",
      havingValue = "true",
      matchIfMissing = true)
  ServletServerContainerFactoryBean createWebSocketContainer(
      @Value("${sea.chat.attachment.maximum-upload-bytes:5242880}") long maximumAttachmentBytes) {
    int bufferSize = messageBufferSize(maximumAttachmentBytes);
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(bufferSize);
    container.setMaxBinaryMessageBufferSize(bufferSize);
    return container;
  }

  private static int messageBufferSize(long maximumAttachmentBytes) {
    if (maximumAttachmentBytes < 1) {
      throw new IllegalArgumentException("maximum-upload-bytes must be positive");
    }
    long base64Bound = maximumAttachmentBytes * 2 + 16384;
    return (int) Math.min(base64Bound, Integer.MAX_VALUE);
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    var registration = registry.addHandler(handler, "/ws/chat");
    if (allowedOrigins.length > 0) {
      registration.setAllowedOrigins(allowedOrigins);
    }
    if (uiHandler != null) {
      var uiRegistration = registry.addHandler(uiHandler, "/ws/ui/chat");
      if (allowedOrigins.length > 0) {
        uiRegistration.setAllowedOrigins(allowedOrigins);
      }
    }
  }
}
