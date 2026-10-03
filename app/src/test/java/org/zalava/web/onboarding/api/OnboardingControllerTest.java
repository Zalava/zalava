package org.zalava.web.onboarding.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.zalava.support.OnboardingWorkspaceComponentTest;
import org.zalava.support.ZalavaComponentTestInitializer;

@OnboardingWorkspaceComponentTest
class OnboardingControllerTest {

  private static final Path WORKSPACE = ZalavaComponentTestInitializer.workspacePath();

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void resetOnboardingArtifacts() throws IOException {
    Files.deleteIfExists(WORKSPACE.resolve("AGENT.private.md"));
    Files.deleteIfExists(WORKSPACE.resolve("private/application.private.yaml"));
  }

  @Test
  void incompleteSetupRedirectsRootToOnboarding() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/onboarding/"));
  }

  @Test
  void rendersWelcomeStepWithJteOnboardingShell() throws Exception {
    mockMvc
        .perform(get("/onboarding/welcome"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Setup</title>")))
        .andExpect(content().string(containsString("htmx.org@2.0.8")))
        .andExpect(content().string(containsString("Setup Wizard")))
        .andExpect(content().string(containsString("Set up")))
        .andExpect(content().string(containsString("href=\"/onboarding/provider\"")));
  }

  @Test
  void rendersProviderSelectionStep() throws Exception {
    mockMvc
        .perform(get("/onboarding/provider"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Choose a model provider")))
        .andExpect(content().string(containsString("OpenAI")))
        .andExpect(content().string(containsString("Anthropic")))
        .andExpect(content().string(containsString("Ollama")))
        .andExpect(content().string(containsString("action=\"/onboarding/provider\"")));
  }

  @Test
  void rendersCoreStarterStepAndRedirectsRemovedDirectSetupSteps() throws Exception {
    mockMvc
        .perform(get("/onboarding/starters"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Choose a starter module")))
        .andExpect(content().string(containsString("No curated starter modules are available")));

    for (String removedStep : java.util.List.of("telegram", "brave", "playwright", "mcp")) {
      mockMvc
          .perform(get("/onboarding/" + removedStep))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/onboarding/welcome"));
    }
  }

  @Test
  void providerSubmissionWithoutSelectionShowsFlashError() throws Exception {
    mockMvc
        .perform(post("/onboarding/provider"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/onboarding/provider"))
        .andExpect(
            flash().attribute("error", "Choose one of the supported providers to continue."));
  }

  @Test
  void arrivingAtCompleteStepViaGetSavesConfigurationAndClearsTheSession() throws Exception {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("onboarding.provider", "anthropic");
    session.setAttribute("onboarding.model", "claude-sonnet-4-6");
    session.setAttribute("onboarding.agent.content", "# Seeded agent instructions");

    mockMvc
        .perform(get("/onboarding/complete").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<strong>Provider:</strong> Anthropic")));

    Assertions.assertThat(WORKSPACE.resolve("AGENT.private.md"))
        .hasContent("# Seeded agent instructions");
    Assertions.assertThat(WORKSPACE.resolve("private/application.private.yaml"))
        .content()
        .contains("anthropic")
        .contains("agent")
        .contains("onboarding")
        .contains("completed");
    Assertions.assertThat(session.getAttribute("onboarding.provider")).isNull();
    Assertions.assertThat(session.getAttribute("onboarding.model")).isNull();
    Assertions.assertThat(session.getAttribute("onboarding.agent.content")).isNull();
  }

  @Test
  void completingTheWizardThroughStartersSavesConfigurationAndRedirectsWithProviderLabel()
      throws Exception {
    MockHttpSession session = new MockHttpSession();

    postAndExpectRedirect(
        "/onboarding/provider", session, "/onboarding/credentials", "provider", "anthropic");
    postAndExpectRedirect(
        "/onboarding/credentials",
        session,
        "/onboarding/agent",
        "model",
        "claude-sonnet-4-6",
        "apiKey",
        "test-api-key");
    postAndExpectRedirect(
        "/onboarding/agent", session, "/onboarding/starters", "agentContent", "# My agent");

    mockMvc
        .perform(post("/onboarding/starters").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/onboarding/complete"))
        .andExpect(flash().attribute("providerLabel", "Anthropic"));

    Assertions.assertThat(WORKSPACE.resolve("AGENT.private.md")).hasContent("# My agent");
    Assertions.assertThat(WORKSPACE.resolve("private/application.private.yaml"))
        .content()
        .contains("spring")
        .contains("ai")
        .contains("anthropic")
        .contains("test-api-key")
        .contains("claude-sonnet-4-6");
  }

  private void postAndExpectRedirect(
      String url, MockHttpSession session, String expectedRedirect, String... params)
      throws Exception {
    MockHttpServletRequestBuilder request = post(url).session(session);
    for (int index = 0; index + 1 < params.length; index += 2) {
      request.param(params[index], params[index + 1]);
    }
    mockMvc
        .perform(request)
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(expectedRedirect));
  }
}
