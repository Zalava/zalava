package org.zalava.mcp;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("mcpHeaderCustomizer")
@EnableConfigurationProperties(StreamableMcpClientProperties.class)
public final class StreamableMcpRequestCustomizer
    implements McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> {
  private final Map<String, Map<String, String>> requestHeadersByConnection;

  public StreamableMcpRequestCustomizer(StreamableMcpClientProperties properties) {
    requestHeadersByConnection =
        properties.connections().entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, connection -> connection.getValue().headers()));
  }

  @Override
  public void customize(
      String connectionName, HttpClientStreamableHttpTransport.Builder transport) {
    Map<String, String> configuredHeaders =
        requestHeadersByConnection.getOrDefault(connectionName, Map.of());
    transport.httpRequestCustomizer(
        (request, method, endpoint, body, context) -> {
          for (Map.Entry<String, String> header : configuredHeaders.entrySet()) {
            request.header(header.getKey(), header.getValue());
          }
        });
  }
}
