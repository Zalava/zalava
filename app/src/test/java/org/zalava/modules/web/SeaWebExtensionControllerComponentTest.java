package org.zalava.modules.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ZalavaModule;
import org.zalava.web.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeaWebExtensionControllerComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.browser.brave.api-key", () -> "test-key");
    registry.add("agent.tools.playwright.enabled", () -> "true");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void rendersAppsIndexWithRegisteredModulePage() throws Exception {
    mockMvc
        .perform(get("/apps"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Zalava Apps")))
        .andExpect(content().string(containsString("Fixture Shopping List")))
        .andExpect(content().string(containsString("/apps/test-web-module/shopping-list")))
        .andExpect(content().string(containsString("<span class=\"sea-nav-label\">Apps</span>")));
  }

  @Test
  void dispatchesGetRouteWithQueryParameters() throws Exception {
    mockMvc
        .perform(get("/apps/test-web-module/shopping-list").queryParam("filter", "open"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Fixture Shopping List")))
        .andExpect(content().string(containsString("GET path=/")))
        .andExpect(content().string(containsString("filter=open")))
        .andExpect(content().string(containsString("class=\"navbar-item is-active\"")));
  }

  @Test
  void dispatchesPostRouteWithPathVariablesAndFormParameters() throws Exception {
    mockMvc
        .perform(
            post("/apps/test-web-module/shopping-list/items/eggs/bought").param("checked", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("POST item=eggs")))
        .andExpect(content().string(containsString("checked=true")));
  }

  @Test
  void returnsNotFoundForMissingModuleAppRoute() throws Exception {
    mockMvc
        .perform(get("/apps/test-web-module/shopping-list/missing"))
        .andExpect(status().isNotFound());
  }

  @Test
  void doesNotAllowModuleRoutesToOverrideCoreScreens() throws Exception {
    mockMvc
        .perform(get("/chat"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Chat</title>")))
        .andExpect(content().string(not(containsString("Fixture Shopping List"))));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestWebExtensionConfiguration {

    @Bean
    ZalavaModule testWebModule() {
      return new TestWebModule();
    }
  }

  private static final class TestWebModule implements ZalavaModule {

    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor(
          "test-web-module",
          "1.0.0",
          "Test Web Module",
          "Module that contributes web extension routes for tests.");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of();
    }

    @Override
    public List<ZalavaWebExtension> webExtensions() {
      return List.of(new ShoppingListExtension());
    }
  }

  private static final class ShoppingListExtension implements ZalavaWebExtension {

    @Override
    public WebExtensionDescriptor descriptor() {
      return new WebExtensionDescriptor(
          "test-web-module",
          "shopping-list-extension",
          "Fixture Shopping List",
          "Fixture module page for shopping-list UI experiments.");
    }

    @Override
    public void register(WebExtensionRegistry registry) {
      registry
          .page("shopping-list")
          .title("Fixture Shopping List")
          .description("Fixture module page for shopping-list UI experiments.")
          .navSection("apps")
          .get(
              "/",
              request ->
                  ZalavaWebResponse.html(
                      """
                            <section class="box dashboard-activity">
                                <h1 class="title is-3">Fixture Shopping List</h1>
                                <p>GET path=%s</p>
                                <p>filter=%s</p>
                            </section>
                            """
                          .formatted(
                              request.path(),
                              request.firstQueryParameter("filter").orElse("none"))))
          .post(
              "/items/{id}/bought",
              request ->
                  ZalavaWebResponse.html(
                      """
                            <section class="box dashboard-activity">
                                <h1 class="title is-3">Fixture Shopping List</h1>
                                <p>POST item=%s</p>
                                <p>checked=%s</p>
                            </section>
                            """
                          .formatted(
                              request.pathVariables().get("id"),
                              request.firstFormParameter("checked").orElse("false"))));
    }
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-web-extension-test-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test workspace info.");
      Path skill = workspace.resolve("skills/test-skill/SKILL.md");
      Files.createDirectories(skill.getParent());
      Files.writeString(
          skill,
          """
                    ---
                    name: test-skill
                    description: Test skill used by component tests.
                    ---

                    # Test Skill

                    Used by component tests.
                    """);
      return workspace;
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create test workspace", ex);
    }
  }
}
