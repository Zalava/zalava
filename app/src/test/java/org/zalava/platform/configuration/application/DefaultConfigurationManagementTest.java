package org.zalava.platform.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.platform.configuration.application.port.out.ConfigurationChangePublisher;
import org.zalava.platform.configuration.application.port.out.ConfigurationStore;

class DefaultConfigurationManagementTest {

  private final ConfigurationStore configurationStore = mock(ConfigurationStore.class);
  private final ConfigurationChangePublisher changePublisher =
      mock(ConfigurationChangePublisher.class);
  private final DefaultConfigurationManagement management =
      new DefaultConfigurationManagement(configurationStore, changePublisher);

  @Test
  void updatesNestedPropertiesThenPublishesThePersistedConfiguration() throws Exception {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put("agent", new LinkedHashMap<>(Map.of("enabled", false)));
    when(configurationStore.read()).thenReturn(configuration);

    management.updateProperties(Map.of("agent.enabled", true, "spring.ai.model", "openai"));

    var writtenConfiguration = org.mockito.ArgumentCaptor.<Map<String, Object>>captor();
    verify(configurationStore).write(writtenConfiguration.capture());
    assertThat(writtenConfiguration.getValue()).containsEntry("agent", Map.of("enabled", true));
    assertThat(writtenConfiguration.getValue())
        .containsEntry("spring", Map.of("ai", Map.of("model", "openai")));
    verify(changePublisher).publishConfigurationChanged(writtenConfiguration.getValue());
  }

  @Test
  void returnsANewTopLevelMapForConfigurationQueries() throws Exception {
    Map<String, Object> stored = new LinkedHashMap<>(Map.of("agent", Map.of("enabled", true)));
    when(configurationStore.read()).thenReturn(stored);

    Map<String, Object> result = management.readApplicationYaml();
    result.put("other", "value");

    assertThat(stored).doesNotContainKey("other");
  }
}
