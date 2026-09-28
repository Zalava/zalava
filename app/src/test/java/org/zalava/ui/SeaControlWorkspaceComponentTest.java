package org.zalava.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import java.util.Map;
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
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver;
import org.zalava.catalog.install.application.port.out.ModuleLocatorReleaseLocator;
import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.ModuleDevelopmentContract;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  SeaControlWorkspaceComponentTest.CatalogTestConfiguration.class,
  AuthenticatedMockMvcTestConfiguration.class
})
@WithMockUser(username = "workspace-admin", roles = "ADMIN")
class SeaControlWorkspaceComponentTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;
  @Autowired private LocalArtifactModuleInstallation localArtifactInstallations;
  @Autowired private org.zalava.accounts.application.port.in.AccountLifecycle accounts;

  @BeforeEach
  void enableBootstrapAdministrator() {
    var administrator =
        accounts
            .findByLoginName("workspace-admin")
            .orElseGet(
                () ->
                    accounts.create(
                        "workspace-admin",
                        "TestBootstrapPassword-123",
                        org.zalava.accounts.domain.AccountRole.ADMIN));
    if (administrator.passwordChangeRequired()) {
      accounts.changePassword(
          administrator.id(), "TestBootstrapPassword-123", "AdministratorPassword-123");
    }
  }

  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("sea.accounts.bootstrap-login", () -> "workspace-admin");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("agent.modules.local-artifact-roots", () -> WORKSPACE.toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void rendersWorkspaceWithCatalogState() throws Exception {
    mockMvc
        .perform(get("/sea/control/workspace"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("SEA provider status")))
        .andExpect(content().string(containsString("External module development")))
        .andExpect(content().string(containsString("Refresh catalog")));
  }

  @Test
  void localModuleInstallationRejectsInvalidInputAndUnknownModuleIds() throws Exception {
    mockMvc
        .perform(
            post("/sea/control/local-module-installations")
                .param("moduleId", "")
                .param("indexYaml", "schemaVersion: 1\nmodules: []")
                .param("artifactPath", "/tmp/none.jar")
                .param("developmentRequestId", "request-1")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(containsString("Module id must contain between 1 and 160 characters")));

    mockMvc
        .perform(
            post("/sea/control/local-module-installations")
                .param("moduleId", "missing-module")
                .param("indexYaml", validIndexYaml())
                .param("artifactPath", "/tmp/none.jar")
                .param("developmentRequestId", "request-1")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    containsString(
                        "Module id is not present in the validated index: missing-module")));
  }

  @Test
  void localModuleInstallationWithoutAcceptedCandidateRendersAnError() throws Exception {
    Path artifact = jarArtifact("sea-module-local-1.2.3.jar");

    mockMvc
        .perform(
            post("/sea/control/local-module-installations")
                .param("moduleId", "sea-module-local")
                .param("indexYaml", validIndexYaml())
                .param("artifactPath", artifact.toString())
                .param("developmentRequestId", "request-1")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    containsString("Development request is not available for binary validation")));
  }

  @Test
  void deniesAndAllowsLocalModuleInstallationRequestsThroughTheWorkspace() throws Exception {
    Path artifact = jarArtifact("sea-module-local-1.2.3.jar");
    LocalArtifactInstallRequest request =
        localArtifactInstallations.create(
            module("sea-module-local", "1.2.3"), artifact.toString(), null);

    mockMvc
        .perform(
            post("/sea/control/local-module-installations/" + request.requestId() + "/deny")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Installation denied")));

    mockMvc
        .perform(
            post("/sea/control/local-module-installations/" + request.requestId() + "/allow")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Local artifact request is not pending")));
  }

  @Test
  void rejectsInvalidLocalModuleProjectInstallations() throws Exception {
    mockMvc
        .perform(
            post("/sea/control/local-module-project-installations")
                .param("projectDirectory", WORKSPACE.resolve("missing-project").toString())
                .param("moduleId", "")
                .param("version", "")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(containsString("Module id must contain between 1 and 2000 characters")));
  }

  @Test
  void moduleReleaseInstallationRequiresACatalogSelection() throws Exception {
    mockMvc
        .perform(
            post("/sea/control/module-release-installations")
                .param("moduleId", "sea-module-unknown")
                .param("version", "9.9.9")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    containsString(
                        "Choose a module and release version from the refreshed catalog")));
  }

  @Test
  void moduleReleaseInstallationCanBeCreatedAndDecidedThroughTheWorkspace() throws Exception {
    mockMvc
        .perform(post("/sea/control/module-release-installations/catalog/refresh").with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/sea/control/module-release-installations/catalog/select")
                .param("moduleId", "sea-module-tika")
                .with(csrf()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/sea/control/module-release-installations")
                .param("moduleId", "sea-module-tika")
                .param("version", "1.0.1")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("sea-module-tika")));

    String requestId = latestModuleReleaseRequestId();

    mockMvc
        .perform(
            post("/sea/control/module-release-installations/" + requestId + "/allow").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Module enabled")));

    mockMvc
        .perform(
            post("/sea/control/module-release-installations/" + requestId + "/deny").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Module release request is not pending")));
  }

  @Test
  void catalogRefreshListsModulesAndSelectionListsReleases() throws Exception {
    mockMvc
        .perform(post("/sea/control/module-release-installations/catalog/refresh").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("sea-module-tika")))
        .andExpect(content().string(containsString("Time")));

    mockMvc
        .perform(
            post("/sea/control/module-release-installations/catalog/select")
                .param("moduleId", "sea-module-tika")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("1.0.1")));

    mockMvc
        .perform(
            post("/sea/control/module-release-installations/catalog/select")
                .param("moduleId", "unknown-module")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    containsString(
                        "Catalog module is not available; refresh the catalog and choose a module")));
  }

  @Test
  void permissionPolicyRevocationRendersErrorsForUnknownRequests() throws Exception {
    mockMvc
        .perform(post("/sea/control/permission-policies/unknown-request/revoke").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(containsString("SEA tool approval request not found: unknown-request")));
  }

  @Test
  void permissionPolicyNarrowingRendersErrorsForUnknownRequests() throws Exception {
    mockMvc
        .perform(
            post("/sea/control/permission-policies/unknown-request/narrow")
                .param("scopeKey", "root")
                .param("scopeValue", "workspace")
                .param("expiresAt", "2026-12-01T00:00:00Z")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(containsString("SEA tool approval request not found: unknown-request")));
  }

  @Test
  void permissionPolicyNarrowingRejectsAnIncompleteScopeConstraint() throws Exception {
    mockMvc
        .perform(
            post("/sea/control/permission-policies/unknown-request/narrow")
                .param("scopeKey", "")
                .param("scopeValue", "workspace")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content().string(containsString("Scope key and value must be provided together")));
  }

  @Test
  void developmentRequestWorkflowIsExposedThroughTheWorkspace() throws Exception {
    String contractJson = contractJson();

    mockMvc
        .perform(
            post("/sea/control/development-requests")
                .param("contractJson", contractJson)
                .param("reason", "Build a local fixture module")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("sea-module-local")))
        .andExpect(content().string(containsString("PREPARED")));

    DevelopmentRequestId created = latestDevelopmentRequestId();
    String requestId = created.value();

    mockMvc
        .perform(
            post("/sea/control/development-requests/revise")
                .param("requestId", requestId)
                .param("contractJson", contractJson)
                .param("reason", "Revised fixture reason")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2")));

    mockMvc
        .perform(post("/sea/control/development-requests/" + requestId + "/begin").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("IN_DEVELOPMENT")));

    mockMvc
        .perform(
            post("/sea/control/development-requests/" + requestId + "/exports")
                .param("workspaceRoot", WORKSPACE.resolve("fixture-export").toString())
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("EXPORTED")));

    mockMvc
        .perform(post("/sea/control/development-requests/" + requestId + "/inspect").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("sea-module-local")));

    mockMvc
        .perform(
            post("/sea/control/development-requests")
                .param("contractJson", "{not-json")
                .param("reason", "Broken contract")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("alert alert-danger")));
  }

  @Test
  void developmentRequestInspectRendersErrorsForUnknownRequests() throws Exception {
    mockMvc
        .perform(post("/sea/control/development-requests/unknown-request/inspect").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Development request not found")));
  }

  private String latestModuleReleaseRequestId() throws IOException {
    Path directory = WORKSPACE.resolve("source-module-installation").resolve("release-requests");
    try (var paths = Files.list(directory)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("No module release request was created"))
          .getFileName()
          .toString()
          .replace(".json", "");
    }
  }

  private DevelopmentRequestId latestDevelopmentRequestId() throws IOException {
    Path directory = WORKSPACE.resolve("module-development").resolve("requests");
    try (var paths = Files.list(directory)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(path -> path.getFileName().toString().replace(".json", ""))
          .sorted()
          .reduce((first, second) -> second)
          .map(DevelopmentRequestId::new)
          .orElseThrow(() -> new IllegalStateException("No development request was created"));
    }
  }

  private Path jarArtifact(String name) throws IOException {
    Path jar = WORKSPACE.resolve(name);
    Files.writeString(jar, "fixture module artifact");
    return jar;
  }

  private static SourceModuleIndex.Module module(String moduleId, String version) {
    return new SourceModuleIndex.Module(
        moduleId,
        version,
        "Local Fixture",
        "Locally developed fixture module",
        URI.create("https://github.com/example/sea-module-local"),
        new SourceModuleIndex.Artifact("ai.sea.modules", moduleId, version),
        null,
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of("type", "object"),
        List.of(new SourceModuleIndex.Factory("local-factory", "test")),
        List.of(
            new SourceModuleIndex.Operation("lookup", "Lookup", false, Map.of("type", "object"))),
        new SourceModuleIndex.Security(List.of()));
  }

  private static String validIndexYaml() {
    return "schemaVersion: 1\n"
        + "modules:\n"
        + "  - moduleId: sea-module-local\n"
        + "    version: 1.2.3\n"
        + "    displayName: Local Fixture\n"
        + "    description: Locally developed fixture module\n"
        + "    supportUrl: https://github.com/example/sea-module-local\n"
        + "    artifact:\n"
        + "      groupId: ai.sea.modules\n"
        + "      artifactId: sea-module-local\n"
        + "      version: 1.2.3\n"
        + "    compatibility:\n"
        + "      seaRuntime: \">=1.0.0\"\n"
        + "    configurationSchema:\n"
        + "      type: object\n"
        + "    factories:\n"
        + "      - factoryId: local-factory\n"
        + "        providerType: test\n"
        + "    operations:\n"
        + "      - name: lookup\n"
        + "        description: Lookup\n"
        + "        sideEffecting: false\n"
        + "        inputSchema:\n"
        + "          type: object\n"
        + "    security:\n"
        + "      permissions: []\n";
  }

  private static String contractJson() {
    ModuleDevelopmentContract contract =
        new ModuleDevelopmentContract(
            new ModuleDevelopmentContract.Module("sea-module-local", "1.2.3"),
            "Fixture evaluation",
            "1.0.0",
            List.of(
                new ModuleDevelopmentContract.Tool(
                    "example_lookup",
                    "Fixture lookup",
                    "{\"type\":\"object\"}",
                    "{\"type\":\"object\"}",
                    List.of("UNKNOWN_TOOL"),
                    List.of(
                        new ModuleDevelopmentContract.ExampleCall(
                            "{}", "{\"value\":\"fixture\"}")))),
            List.of(new ModuleDevelopmentContract.ExpectedError("UNKNOWN_TOOL", "Unknown tool")),
            List.of(
                new ModuleDevelopmentContract.AcceptanceScenario(
                    "happy",
                    "{}",
                    List.of(
                        new ModuleDevelopmentContract.ResponseAssertion(
                            "$.value", "exists", "true")),
                    null)),
            new ModuleDevelopmentContract.OperationalRequirements(
                1_000L, 10_000L, false, List.of(), false, false, "25"),
            new ModuleDevelopmentContract.DeliveryRequirements(
                "jar", "fixture.jar", "1", false, Map.of()));
    try {
      return JSON.writeValueAsString(contract);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to serialize contract", ex);
    }
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-control-workspace-test-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
      Files.writeString(
          skill.resolve("SKILL.md"),
          """
                    ---
                    name: test-skill
                    description: Minimal component test skill.
                    ---

                    # Test Skill
                    """);
      return workspace;
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
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
          return List.of(
              new Module("sea-module-time", "Time", "Time tools"),
              new Module("sea-module-tika", "Tika", "Content extraction"));
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
      return (uri, token) ->
          new ModuleReleaseIndex(
              1,
              "sea-module-tika",
              List.of(release("1.0.1", "v1.0.1"), release("1.1.0", "v1.1.0")));
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
