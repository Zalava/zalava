package org.zalava.onboarding.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.zalava.SupportedProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class S3_CredentialsStepTest {

  private final MockEnvironment env = new MockEnvironment();
  private final S3_CredentialsStep step = new S3_CredentialsStep(env, provider -> Optional.empty());

  @Test
  void prepareModelIgnoresAnUnknownProvider() {
    Map<String, Object> model = new HashMap<>();

    step.prepareModel(new HashMap<>(), model);

    assertThat(model).isEmpty();
  }

  @Test
  void prepareModelExposesProviderDefaultsAndExistingConfiguration() {
    env.setProperty("spring.ai.anthropic.chat.options.model", "claude-opus-4-8");
    env.setProperty("spring.ai.anthropic.api-key", "existing-key");
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "anthropic");
    Map<String, Object> model = new HashMap<>();

    step.prepareModel(session, model);

    assertThat(model.get("selectedProvider")).isEqualTo("anthropic");
    assertThat(model.get("providerLabel")).isEqualTo("Anthropic");
    assertThat(model.get("providerApiPropertyKey")).isEqualTo("spring.ai.anthropic.api-key");
    assertThat(model.get("chatModelPropertyKey"))
        .isEqualTo("spring.ai.anthropic.chat.options.model");
    assertThat(model.get("requiresApiKey")).isEqualTo(true);
    assertThat(model.get("apiKey")).isEqualTo("existing-key");
    assertThat(model.get("model")).isEqualTo("claude-opus-4-8");
  }

  @Test
  void prepareModelFallsBackToTheProviderDefaultModel() {
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "openai");
    Map<String, Object> model = new HashMap<>();

    step.prepareModel(session, model);

    assertThat(model.get("model")).isEqualTo(SupportedProvider.OPENAI.defaultModel());
    assertThat(model.get("requiresApiKey")).isEqualTo(true);
  }

  @Test
  void processStepRequiresAPreviouslySelectedProvider() {
    assertThat(step.processStep(Map.of("model", "gpt-5.4", "apiKey", "k"), new HashMap<>()))
        .isEqualTo("Provider selection is missing. Please go back and select a provider.");
  }

  @Test
  void processStepRequiresAModel() {
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "openai");

    assertThat(step.processStep(Map.of("model", "  ", "apiKey", "k"), session))
        .isEqualTo("Enter a model to continue.");
  }

  @Test
  void processStepRequiresAnApiKeyForProvidersThatNeedOne() {
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "openai");

    assertThat(step.processStep(Map.of("model", "gpt-5.4"), session))
        .isEqualTo("Enter an API key to continue.");
  }

  @Test
  void processStepStoresModelAndKeyWithoutAnApiKeyForLocalProviders() {
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "ollama");

    assertThat(step.processStep(Map.of("model", "qwen3.5:27b"), session)).isNull();

    assertThat(session.get(S2_ProviderStep.SESSION_MODEL)).isEqualTo("qwen3.5:27b");
    assertThat(session.get(S2_ProviderStep.SESSION_API_KEY)).isEqualTo("");
  }

  @Test
  void systemWideTokenIsUnavailableWithoutHostCredentials() {
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "anthropic");

    assertThat(
            step.processStep(
                Map.of("model", "claude-sonnet-4-6", "useSystemToken", "true"), session))
        .isEqualTo("System token is no longer available. Please enter your API key manually.");
  }

  @Test
  void processStepUsesAnInjectedSystemWideToken() {
    S3_CredentialsStep tokenAwareStep =
        new S3_CredentialsStep(
            env,
            provider ->
                Optional.of(
                    new SupportedProvider.SystemWideToken(
                        "Claude Code", "<claude-code-bearer-token>")));
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "anthropic");

    assertThat(
            tokenAwareStep.processStep(
                Map.of("model", "claude-sonnet-4-6", "useSystemToken", "true"), session))
        .isNull();
    assertThat(session.get(S2_ProviderStep.SESSION_API_KEY))
        .isEqualTo("<claude-code-bearer-token>");
  }

  @Test
  void acceptedSubmissionStoresModelAndApiKey() {
    Map<String, Object> session = new HashMap<>();
    session.put(S2_ProviderStep.SESSION_PROVIDER, "openai");

    assertThat(step.processStep(Map.of("model", "gpt-5.4", "apiKey", " sk-key "), session))
        .isNull();

    assertThat(session.get(S2_ProviderStep.SESSION_MODEL)).isEqualTo("gpt-5.4");
    assertThat(session.get(S2_ProviderStep.SESSION_API_KEY)).isEqualTo("sk-key");
  }

  @Test
  void metadataExposesStepIdentity() {
    assertThat(step.getStepId()).isEqualTo("credentials");
    assertThat(step.getStepTitle()).isEqualTo("Credentials");
    assertThat(step.getTemplatePath()).isEqualTo("onboarding/steps/S3-credentials");
  }

  @Test
  void saveConfigurationIsAReleaseOnlyStepAndPersistsNothing() throws Exception {
    var commands =
        mock(org.zalava.configuration.application.port.in.ConfigurationCommands.class);

    step.saveConfiguration(new HashMap<>(), commands);

    verify(commands, org.mockito.Mockito.never()).updateProperties(anyMap());
  }
}
