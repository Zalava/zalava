package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.assistant.models.configuration.adapter.out.filesystem.ModelProviderStore;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.ComponentTestAccounts;
import org.zalava.support.SecureMutableWorkspaceComponentTest;
import org.zalava.support.ZalavaComponentTestInitializer;

@SecureMutableWorkspaceComponentTest
class ModelProviderSettingsComponentTest {
  @Autowired MockMvc mvc;
  @Autowired ComponentTestAccounts accounts;
  ModelProviderStore store = new ModelProviderStore(ZalavaComponentTestInitializer.workspacePath());

  @BeforeEach
  @AfterEach
  void clear() throws Exception {
    Files.deleteIfExists(store.path());
  }

  @Test
  void rendersIntegratedProviderChoicesAndRedirectsOldWizard() throws Exception {
    mvc.perform(get("/settings").param("section", "provider"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Save provider")))
        .andExpect(content().string(containsString("Google Gemini")))
        .andExpect(content().string(containsString("Amazon Bedrock Converse")))
        .andExpect(content().string(containsString("Sign out")))
        .andExpect(content().string(not(containsString("Setup Wizard"))));
    for (String path :
        new String[] {
          "/onboarding", "/onboarding/", "/onboarding/provider", "/onboarding/complete"
        }) mvc.perform(get(path)).andExpect(redirectedUrl("/settings?section=provider"));
    mvc.perform(post("/onboarding/provider")).andExpect(status().isMethodNotAllowed());
    mvc.perform(get("/")).andExpect(redirectedUrl("/chat"));
    mvc.perform(get("/settings").param("section", "provider").param("provider", "bad"))
        .andExpect(status().isOk());
  }

  @Test
  void persistsProviderSecretWithoutEchoAndShowsRestartState() throws Exception {
    mvc.perform(
            post("/settings/provider")
                .param("provider", "openai")
                .param("model", "test-model")
                .param("apiKey", "not-a-real-secret")
                .param("baseUrl", "https://example.test/v1"))
        .andExpect(redirectedUrl("/settings?section=provider"))
        .andExpect(flash().attribute("settingsMessage", containsString("Restart Zalava")));
    assertThat(store.read()).containsEntry("provider", "openai");
    mvc.perform(get("/settings").param("section", "provider"))
        .andExpect(content().string(not(containsString("not-a-real-secret"))))
        .andExpect(content().string(containsString("Leave blank to keep it")))
        .andExpect(content().string(containsString("Restart required")));
    mvc.perform(
            post("/settings/provider")
                .param("provider", "openai")
                .param("model", "replacement")
                .param("baseUrl", "https://example.test/v1"))
        .andExpect(redirectedUrl("/settings?section=provider"));
    assertThat(Files.readString(store.path()))
        .contains("not-a-real-secret")
        .contains("replacement");
  }

  @Test
  void refusesInvalidCsrfWithoutSaving() throws Exception {
    mvc.perform(
            post("/settings/provider")
                .param("provider", "ollama")
                .param("model", "test")
                .param("baseUrl", "http://localhost:11434")
                .with(csrf().useInvalidToken()))
        .andExpect(status().isForbidden());
    assertThat(store.path()).doesNotExist();
  }

  @Test
  void validatesInputAndPreventsMemberConfiguration() throws Exception {
    mvc.perform(post("/settings/provider").param("provider", "openai"))
        .andExpect(flash().attribute("settingsError", "Enter model."));
    var member = accounts.authenticatedAs(accounts.newActivated(AccountRole.MEMBER));
    mvc.perform(get("/settings").param("section", "provider").with(member))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/settings/provider")
                .param("provider", "ollama")
                .param("model", "test")
                .param("baseUrl", "http://localhost:11434")
                .with(member))
        .andExpect(status().isForbidden());
    assertThat(store.path()).doesNotExist();
  }
}
