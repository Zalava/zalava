package org.zalava.onboarding.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.zalava.SupportedProvider;
import org.zalava.configuration.application.port.in.ConfigurationCommands;

class S2_ProviderStepTest {

  private final MockEnvironment env = new MockEnvironment();
  private final S2_ProviderStep step = new S2_ProviderStep(env);

  @Test
  void exposesProviderMetadataAndDefaultsToConfiguredChatModel() {
    env.setProperty("spring.ai.model.chat", "anthropic");

    Map<String, Object> model = new HashMap<>();
    step.prepareModel(new HashMap<>(), model);

    assertThat(model.get("providers")).isEqualTo(SupportedProvider.supportedAgents());
    assertThat(model.get("selectedProvider")).isEqualTo("anthropic");
    assertThat(step.getStepId()).isEqualTo("provider");
    assertThat(step.getStepTitle()).isEqualTo("Provider");
    assertThat(step.getTemplatePath()).isEqualTo("onboarding/steps/S2-provider");
  }

  @Test
  void rejectsMissingAndUnknownProviderSelection() {
    Map<String, Object> session = new HashMap<>();

    assertThat(step.processStep(Map.of(), session))
        .isEqualTo("Choose one of the supported providers to continue.");
    assertThat(step.processStep(Map.of("provider", " "), session))
        .isEqualTo("Choose one of the supported providers to continue.");
    assertThat(step.processStep(Map.of("provider", "not-a-provider"), session))
        .isEqualTo("Choose one of the supported providers to continue.");
    assertThat(session).isEmpty();
  }

  @Test
  void selectingADifferentProviderClearsDownstreamModelAndCredentialChoices() {
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "anthropic");
    session.put(S2_ProviderStep.SESSION_MODEL, "claude-sonnet-4-6");
    session.put(S2_ProviderStep.SESSION_API_KEY, "stale-key");

    assertThat(step.processStep(Map.of("provider", "Ollama"), session)).isNull();

    assertThat(session.get(S2_ProviderStep.SESSION_PROVIDER)).isEqualTo("ollama");
    assertThat(session)
        .doesNotContainKeys(S2_ProviderStep.SESSION_MODEL, S2_ProviderStep.SESSION_API_KEY);
  }

  @Test
  void resubmittingTheSameProviderKeepsExistingChoices() {
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "anthropic");
    session.put(S2_ProviderStep.SESSION_MODEL, "claude-sonnet-4-6");
    session.put(S2_ProviderStep.SESSION_API_KEY, "kept-key");

    assertThat(step.processStep(Map.of("provider", "anthropic"), session)).isNull();

    assertThat(session.get(S2_ProviderStep.SESSION_MODEL)).isEqualTo("claude-sonnet-4-6");
    assertThat(session.get(S2_ProviderStep.SESSION_API_KEY)).isEqualTo("kept-key");
  }

  @SuppressWarnings("unchecked")
  @Test
  void savesPersistedProviderPropertiesSkippingBlankValues() throws Exception {
    ConfigurationCommands commands = mock(ConfigurationCommands.class);
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "ollama");
    session.put(S2_ProviderStep.SESSION_MODEL, "qwen3.5:27b");

    step.saveConfiguration(session, commands);

    verify(commands).updateProperties(anyMap());
    org.mockito.ArgumentCaptor<Map<String, Object>> captor =
        org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(commands).updateProperties(captor.capture());
    assertThat(captor.getValue())
        .containsEntry("spring.ai.ollama.chat.options.model", "qwen3.5:27b")
        .containsEntry("spring.ai.model.chat", "ollama")
        .doesNotContainKey("spring.ai.ollama.api-key");
  }

  @Test
  void saveWithoutARecognizedProviderDoesNothing() throws Exception {
    ConfigurationCommands commands = mock(ConfigurationCommands.class);

    step.saveConfiguration(new HashMap<>(), commands);

    verifyNoInteractions(commands);
  }
}
