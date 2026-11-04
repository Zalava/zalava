package org.zalava.modules.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;
import org.zalava.api.extensions.web.*;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.web.application.DefaultModulePageAccess;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;
import org.zalava.modules.web.application.port.out.ModulePageAccessStore;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(
    org.zalava.support.AuthenticatedMockMvcTestConfiguration.class)
class ZalavaWebExtensionControllerComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountLifecycle accounts;
  @Autowired private ModulePageAccess access;
  @Autowired private ModulePageAccessStore store;

  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> "module-web-test");
    registry.add("zalava.accounts.bootstrap-password", () -> "ModuleWebTestPassword-123");
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
        .andExpect(
            content().string(containsString("<span class=\"zalava-nav-label\">Apps</span>")));
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
  void suppliesHostCsrfTokenForNativeModuleFormsAndRejectsInvalidSubmissions() throws Exception {
    mockMvc
        .perform(get("/apps/test-web-module/shopping-list"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("name=\"zalava-csrf-token\"")))
        .andExpect(content().string(containsString("name=\"zalava-csrf-parameter\"")))
        .andExpect(content().string(containsString("form.appendChild(field)")));
    mockMvc
        .perform(
            post("/apps/test-web-module/shopping-list/items/eggs/bought")
                .param("checked", "true")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()
                        .useInvalidToken()))
        .andExpect(status().isForbidden());
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

  @Test
  void memberPageAccessControlsDiscoveryAndEveryRouteWithOverrides() throws Exception {
    String password = "ModuleWebTestPassword-123";
    var admin = accounts.create("page-admin-" + UUID.randomUUID(), password, AccountRole.ADMIN);
    var member = accounts.create("page-member-" + UUID.randomUUID(), password, AccountRole.MEMBER);
    accounts.changePassword(admin.id(), password, password + "x");
    accounts.changePassword(member.id(), password, password + "x");
    String administrator = admin.id().toString();
    String individual = member.id().toString();
    String module = "test-web-module", page = "shopping-list";
    var memberUser = user(member.loginName()).roles("MEMBER");
    var adminUser = user(admin.loginName()).roles("ADMIN");
    mockMvc
        .perform(get("/apps").with(memberUser))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Fixture Shopping List"))));
    mockMvc
        .perform(get("/apps/test-web-module/shopping-list").with(memberUser))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/apps/test-web-module/shopping-list/items/eggs/bought")
                .with(memberUser)
                .with(csrf()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/zalava/accounts/app-access/default")
                .with(memberUser)
                .with(csrf())
                .param("moduleId", module)
                .param("pageId", page)
                .param("allowed", "true"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/zalava/accounts/app-access/default")
                .with(adminUser)
                .with(csrf().useInvalidToken())
                .param("moduleId", module)
                .param("pageId", page)
                .param("allowed", "true"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/zalava/accounts/app-access/default")
                .with(adminUser)
                .with(csrf())
                .param("moduleId", module)
                .param("pageId", page)
                .param("allowed", "true"))
        .andExpect(status().is3xxRedirection());
    mockMvc
        .perform(get("/apps").with(memberUser))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Fixture Shopping List")));
    mockMvc
        .perform(get("/apps/test-web-module/shopping-list").with(memberUser))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/apps/test-web-module/shopping-list/items/eggs/bought")
                .with(memberUser)
                .with(csrf())
                .param("checked", "true"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/apps/test-web-module/shopping-list/items/eggs/bought")
                .with(memberUser)
                .with(csrf().useInvalidToken()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/zalava/accounts/permissions").with(adminUser).param("subject", individual))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Fixture Shopping List")))
        .andExpect(content().string(containsString("Role default")));
    mockMvc
        .perform(
            post("/zalava/accounts/permissions/app-access")
                .with(memberUser)
                .with(csrf())
                .param("subject", individual)
                .param("moduleId", module)
                .param("pageId", page)
                .param("decision", "DENY"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/zalava/accounts/permissions/app-access")
                .with(adminUser)
                .with(csrf().useInvalidToken())
                .param("subject", individual)
                .param("moduleId", module)
                .param("pageId", page)
                .param("decision", "DENY"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/zalava/accounts/permissions/app-access")
                .with(adminUser)
                .with(csrf())
                .param("subject", individual)
                .param("moduleId", module)
                .param("pageId", page)
                .param("decision", "DENY"))
        .andExpect(status().is3xxRedirection());
    mockMvc
        .perform(
            get("/apps/test-web-module/shopping-list")
                .with(memberUser)
                .with(csrf().useInvalidToken()))
        .andExpect(status().isForbidden());
    access.setOverride(administrator, individual, module, page, "INHERIT");
    mockMvc
        .perform(get("/apps/test-web-module/shopping-list").with(memberUser))
        .andExpect(status().isOk());
    access.setDefault(administrator, module, page, false);
    access.setOverride(administrator, individual, module, page, "ALLOW");
    mockMvc
        .perform(get("/apps/test-web-module/shopping-list").with(memberUser))
        .andExpect(status().isOk());
    var unavailable = new DefaultModulePageAccess(accounts, mock(WebExtensionRoutes.class), store);
    assertThat(unavailable.pages(administrator))
        .anyMatch(
            p ->
                p.moduleId().equals(module)
                    && p.pageId().equals(page)
                    && p.description().contains("unavailable"));
    assertThatThrownBy(() -> unavailable.setDefault(administrator, module, page, true))
        .isInstanceOf(IllegalArgumentException.class);
    unavailable.setDefault(administrator, module, page, false);
    unavailable.setOverride(administrator, individual, module, page, "DENY");
    assertThatThrownBy(() -> access.setOverride(administrator, individual, module, page, "BAD"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> access.setDefault(administrator, "../bad", page, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> access.setDefault(individual, module, page, true))
        .isInstanceOf(SecurityException.class);
    accounts.setEnabled(member.id(), false);
    mockMvc
        .perform(get("/apps/test-web-module/shopping-list").with(memberUser))
        .andExpect(status().is3xxRedirection());
    access.setOverride(administrator, individual, module, page, "INHERIT");
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
      Path workspace = Files.createTempDirectory("zalava-web-extension-test-");
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
