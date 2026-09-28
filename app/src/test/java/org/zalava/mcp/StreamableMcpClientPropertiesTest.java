package org.zalava.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamableMcpClientPropertiesTest {

  @Test
  void copiesConnectionAndHeaderMapsAndNormalizesMissingValues() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer test");
    Map<String, StreamableMcpClientProperties.ClientDefinition> connections = new LinkedHashMap<>();
    connections.put(
        "calendar", new StreamableMcpClientProperties.ClientDefinition(null, "/mcp", headers));

    StreamableMcpClientProperties properties = new StreamableMcpClientProperties(connections);
    headers.clear();
    connections.clear();

    StreamableMcpClientProperties.ClientDefinition calendar =
        properties.connections().get("calendar");
    assertThat(calendar.url()).isEmpty();
    assertThat(calendar.endpoint()).isEqualTo("/mcp");
    assertThat(calendar.headers()).containsEntry("Authorization", "Bearer test");
    assertThat(new StreamableMcpClientProperties(null).connections()).isEmpty();
  }
}
