package org.zalava.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver;
import org.zalava.catalog.install.application.port.out.ModuleLocatorReleaseLocator;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  ModuleMarketplaceComponentTest.CatalogTestConfiguration.class,
  AuthenticatedMockMvcTestConfiguration.class
})
@WithMockUser(username = "modules-admin", roles = "ADMIN")
class ModuleMarketplaceComponentTest {

  private static final String MODULE_ID = "sea-module-tika";
  private static final Pattern REQUEST_ID =
      Pattern.compile("data-installation-request=\"([^\"]+)\"");
  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountLifecycle accounts;
  @Autowired private org.zalava.support.ComponentTestAccounts componentTestAccounts;

  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("sea.accounts.bootstrap-login", () -> "modules-admin");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add(
        "sea.module-configuration.root",
        () -> WORKSPACE.resolve("module-configuration").toString());
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("agent.modules.local-artifact-roots", () -> WORKSPACE.toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @BeforeEach
  void enableBootstrapAdministrator() {
    var administrator =
        accounts
            .findByLoginName("modules-admin")
            .orElseGet(
                () ->
                    accounts.create(
                        "modules-admin", "TestBootstrapPassword-123", AccountRole.ADMIN));
    if (administrator.passwordChangeRequired()) {
      accounts.changePassword(
          administrator.id(), "TestBootstrapPassword-123", "AdministratorPassword-123");
    }
  }

  @Test
  void refreshListsCatalogModules() throws Exception {
    mockMvc.perform(post("/modules/catalog/refresh")).andExpect(status().is3xxRedirection());
    mockMvc
        .perform(get("/modules"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-catalog-module=\"sea-module-tika\"")))
        .andExpect(content().string(containsString("Content extraction")))
        .andExpect(content().string(containsString("Check catalog")));
  }

  @Test
  void rejectsVersionThatIsNotInTheRefreshedCatalog() throws Exception {
    refreshCatalog();
    long before = installationRequestCount();
    mockMvc
        .perform(
            post("/modules/installation-requests")
                .param("moduleId", MODULE_ID)
                .param("version", "9.9.9"))
        .andExpect(status().is3xxRedirection());
    assertThat(installationRequestCount()).isEqualTo(before);
  }

  @Test
  void directInstallationEnablesTheModuleAndShowsArtifactIdentity() throws Exception {
    refreshCatalog();
    requestInstallation(MODULE_ID, "1.1.0");
    assertThat(modulesHtml())
        .contains("data-enabled-module=\"sea-module-tika\"")
        .contains("data-request-status>SUCCEEDED")
        .contains("sha256:");
  }

  @Test
  void directInstallationDoesNotCreateAPendingApproval() throws Exception {
    refreshCatalog();
    requestInstallation(MODULE_ID, "1.1.0");
    assertThat(modulesHtml()).contains("data-request-status>SUCCEEDED").doesNotContain("PENDING");
  }

  @Test
  void detailReportsAnAvailableUpdate() throws Exception {
    refreshCatalog();
    requestInstallation(MODULE_ID, "1.0.1");
    String requestId = firstRequestId(modulesHtml());
    mockMvc
        .perform(post("/modules/installation-requests/" + requestId + "/allow"))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/modules/" + MODULE_ID))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-update-available")))
        .andExpect(content().string(containsString("Update available: 1.1.0")))
        .andExpect(content().string(containsString("data-release-version")));
  }

  @Test
  void disablingAnEnabledModuleRemovesItAndRequiresARestart() throws Exception {
    refreshCatalog();
    requestInstallation(MODULE_ID, "1.1.0");
    String requestId = firstRequestId(modulesHtml());
    mockMvc
        .perform(post("/modules/installation-requests/" + requestId + "/allow"))
        .andExpect(status().is3xxRedirection());
    assertThat(modulesHtml()).contains("data-enabled-module=\"sea-module-tika\"");

    mockMvc
        .perform(post("/modules/" + MODULE_ID + "/disable"))
        .andExpect(status().is3xxRedirection());

    String afterDisable = modulesHtml();
    assertThat(afterDisable).doesNotContain("data-enabled-module=\"sea-module-tika\"");
    assertThat(afterDisable).contains("data-metric=\"enabled-modules\">0<");
  }

  @Test
  void membersCannotReachModuleManagement() throws Exception {
    var member =
        componentTestAccounts.authenticatedAs(
            componentTestAccounts.newActivated(AccountRole.MEMBER));
    mockMvc.perform(get("/modules").with(member)).andExpect(status().isForbidden());
    mockMvc
        .perform(post("/modules/catalog/refresh").with(member))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/modules/" + MODULE_ID).with(member)).andExpect(status().isForbidden());
    mockMvc
        .perform(post("/modules/" + MODULE_ID + "/disable").with(member))
        .andExpect(status().isForbidden());
  }

  private void refreshCatalog() throws Exception {
    mockMvc.perform(post("/modules/catalog/refresh")).andExpect(status().is3xxRedirection());
  }

  private void requestInstallation(String moduleId, String version) throws Exception {
    mockMvc
        .perform(
            post("/modules/installation-requests")
                .param("moduleId", moduleId)
                .param("version", version))
        .andExpect(status().is3xxRedirection());
  }

  private String modulesHtml() throws Exception {
    return mockMvc
        .perform(get("/modules"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long installationRequestCount() throws Exception {
    MvcResult result = mockMvc.perform(get("/modules")).andExpect(status().isOk()).andReturn();
    Matcher matcher =
        Pattern.compile("data-metric=\"installation-requests\">(\\d+)<")
            .matcher(result.getResponse().getContentAsString());
    assertThat(matcher.find()).isTrue();
    return Long.parseLong(matcher.group(1));
  }

  private static String firstRequestId(String html) {
    Matcher matcher = REQUEST_ID.matcher(html);
    assertThat(matcher.find()).as("a rendered installation request").isTrue();
    return matcher.group(1);
  }

  private static Path createWorkspace() {
    try {
      Path root = Files.createTempDirectory("sea-module-marketplace-");
      Files.writeString(root.resolve("AGENT.md"), "Module marketplace component workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class CatalogTestConfiguration {

    @Bean
    @Primary
    ModuleLocatorReleaseLocator deterministicModuleLocator() {
      return new ModuleLocatorReleaseLocator() {
        @Override
        public List<Module> modules() {
          return List.of(new Module("sea-module-tika", "Tika", "Content extraction"));
        }

        @Override
        public ResolvedModule resolve(String moduleId) {
          return new ResolvedModule(
              moduleId,
              moduleId,
              "description",
              URI.create("https://example.test/" + moduleId + "/releases/index.yaml"),
              URI.create("https://example.test/maven/" + moduleId));
        }
      };
    }

    @Bean
    @Primary
    ModuleReleaseIndexRetrieval deterministicReleaseIndexes() {
      return (uri, token) -> {
        String moduleId = uri.getPath().split("/")[1];
        return new ModuleReleaseIndex(
            1, moduleId, List.of(release("1.0.1", "v1.0.1"), release("1.1.0", "v1.1.0")));
      };
    }

    @Bean
    @Primary
    CuratedMavenArtifactResolver deterministicArtifacts() {
      return new CuratedMavenArtifactResolver() {
        @Override
        public ResolvedArtifact resolve(Request request) {
          try {
            Path jar = Files.createTempFile("sea-module-release-", ".jar");
            Files.writeString(jar, "fixture module artifact");
            return new ResolvedArtifact(jar.toString(), "sha256:" + fixtureDigest());
          } catch (IOException ex) {
            throw new IllegalStateException("Unable to stage fixture artifact", ex);
          }
        }

        @Override
        public void discard(ResolvedArtifact artifact) {}
      };
    }

    private static String fixtureDigest() {
      try {
        return HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        "fixture module artifact"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      } catch (java.security.NoSuchAlgorithmException ex) {
        throw new IllegalStateException(ex);
      }
    }

    private static ModuleReleaseIndex.Release release(String version, String tag) {
      return new ModuleReleaseIndex.Release(
          version,
          tag,
          new ModuleReleaseIndex.Artifact("org.example", "module", version, fixtureDigest()),
          new ModuleReleaseIndex.Source(
              URI.create("https://github.com/example/module"), "Apache-2.0"),
          new ModuleReleaseIndex.Compatibility(">=1.0.0 <2.0.0"),
          new ModuleReleaseIndex.Security(List.of()));
    }
  }
}
