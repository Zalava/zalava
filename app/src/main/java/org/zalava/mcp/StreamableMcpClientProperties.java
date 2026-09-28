package org.zalava.mcp;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.ai.mcp.client.streamable-http")
public record StreamableMcpClientProperties(Map<String, ClientDefinition> connections) {
  public StreamableMcpClientProperties {
    connections = connections == null ? Map.of() : Map.copyOf(connections);
  }

  public record ClientDefinition(String url, String endpoint, Map<String, String> headers) {
    public ClientDefinition {
      url = url == null ? "" : url;
      headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
  }
}
