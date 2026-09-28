package org.zalava.control.adapter.in.http;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.ModuleDescriptor;
import org.zalava.PromptDescriptor;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.ResourceDescriptor;
import org.zalava.SeaModule;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.SeaVerificationContributor;
import org.zalava.SeaVerificationDescriptor;
import org.zalava.SeaVerificationStep;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver;
import org.zalava.catalog.install.application.port.out.ModuleLocatorReleaseLocator;
import org.zalava.development.ModuleDevelopmentContract;
import org.zalava.development.application.port.in.DevelopmentRequestManagement;
import org.zalava.runtime.LoadedSeaProvider;
import org.zalava.runtime.SeaRuntime;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  DevelopmentAdminRestApiComponentTest.RuntimeTestConfiguration.class,
  AuthenticatedMockMvcTestConfiguration.class
})
@WithMockUser(username = "development-admin", roles = "ADMIN")
class DevelopmentAdminRestApiComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;
  @Autowired private SeaRuntime seaRuntime;
  @Autowired private SeaToolApprovalRequests approvals;
  @Autowired private LocalArtifactModuleInstallation localArtifactInstallations;
  @Autowired private DevelopmentRequestManagement developmentRequests;
  @Autowired private org.zalava.accounts.application.port.in.AccountLifecycle accounts;

  @BeforeEach
  void enableBootstrapAdministrator() {
    var administrator =
        accounts
            .findByLoginName("development-admin")
            .orElseGet(
                () ->
                    accounts.create(
                        "development-admin",
                        "TestBootstrapPassword-123",
                        org.zalava.accounts.domain.AccountRole.ADMIN));
    if (administrator.passwordChangeRequired()) {
      accounts.changePassword(
          administrator.id(), "TestBootstrapPassword-123", "AdministratorPassword-123");
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("agent.modules.local-artifact-roots", () -> WORKSPACE.toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("sea.accounts.bootstrap-login", () -> "development-admin");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void exposesRuntimeQueriesForLoadedModulesAndProviders() throws Exception {
    mockMvc
        .perform(get("/api/sea/modules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].moduleId").value("test-module"));
    mockMvc
        .perform(get("/api/sea/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].provider.providerId").value("scoped-provider"));
    mockMvc
        .perform(get("/api/sea/providers/scoped-provider"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider.providerId").value("scoped-provider"))
        .andExpect(jsonPath("$.module.moduleId").value("test-module"))
        .andExpect(jsonPath("$.factory.factoryId").value("local-factory"));
    mockMvc
        .perform(get("/api/sea/providers/scoped-provider/tools"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("write"));
    mockMvc
        .perform(get("/api/sea/providers/scoped-provider/resources"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].uri").value("sea://scoped/example"));
    mockMvc
        .perform(get("/api/sea/providers/scoped-provider/prompts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("example"));
    mockMvc.perform(get("/api/sea/providers/missing-provider")).andExpect(status().isNotFound());
  }

  @Test
  void invokesToolsAndDecidesPermissionRequests() throws Exception {
    mockMvc
        .perform(
            post("/api/sea/providers/scoped-provider/tools/write/invoke")
                .contentType("application/json")
                .content("{\"arguments\":{\"path\":\"test\"}}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.toolName").value("write"));

    String requestId =
        approvals.recentEntries().stream()
            .filter(entry -> entry.toolName().equals("write"))
            .findFirst()
            .orElseThrow()
            .requestId();

    mockMvc
        .perform(post("/api/sea/permission-requests/" + requestId + "/allow"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(post("/api/sea/permission-requests/" + requestId + "/deny"))
        .andExpect(status().isConflict());
  }

  @Test
  void allowToolAndDenyPermissionRequestsAreExposed() throws Exception {
    // A deny decision is recorded and returned to the caller.
    mockMvc
        .perform(
            post("/api/sea/providers/scoped-provider/tools/write/invoke")
                .contentType("application/json")
                .content("{\"actorId\":\"deny-actor\",\"arguments\":{\"path\":\"deny\"}}"))
        .andExpect(status().isAccepted());
    String denyId =
        approvals.recentEntries().stream()
            .filter(entry -> entry.decision() == SeaToolApprovalRequests.Decision.PENDING)
            .filter(entry -> entry.actorId().equals("deny-actor"))
            .findFirst()
            .orElseThrow()
            .requestId();
    mockMvc
        .perform(post("/api/sea/permission-requests/" + denyId + "/deny"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("denied"));

    // A saved tool policy permits subsequent invocations for the same actor and tool.
    mockMvc
        .perform(
            post("/api/sea/providers/scoped-provider/tools/write/invoke")
                .contentType("application/json")
                .content("{\"actorId\":\"policy-actor\",\"arguments\":{\"path\":\"first\"}}"))
        .andExpect(status().isAccepted());
    String policyId =
        approvals.recentEntries().stream()
            .filter(entry -> entry.decision() == SeaToolApprovalRequests.Decision.PENDING)
            .filter(entry -> entry.actorId().equals("policy-actor"))
            .findFirst()
            .orElseThrow()
            .requestId();
    mockMvc
        .perform(post("/api/sea/permission-requests/" + policyId + "/allow-tool"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/sea/providers/scoped-provider/tools/write/invoke")
                .contentType("application/json")
                .content("{\"actorId\":\"policy-actor\",\"arguments\":{\"path\":\"first\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void readsResourcesAndResolvesPrompts() throws Exception {
    mockMvc
        .perform(
            post("/api/sea/providers/scoped-provider/resources/read")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/sea/providers/scoped-provider/resources/read")
                .contentType("application/json")
                .content("{\"uri\":\"sea://scoped/example\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            post("/api/sea/providers/scoped-provider/prompts/example/resolve")
                .contentType("application/json")
                .content("{\"arguments\":{}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            post("/api/sea/providers/missing-provider/resources/read")
                .contentType("application/json")
                .content("{\"uri\":\"sea://x\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void moduleInstallationAdminRejectsInvalidLocalArtifactRequests() throws Exception {
    mockMvc
        .perform(
            post("/api/sea/local-module-installations")
                .contentType("application/json")
                .content(
                    "{\"moduleId\":\"sea-module-local\",\"indexYaml\":\"schemaVersion: 1\\nmodules: []\",\"artifactPath\":\"/tmp/none.jar\",\"developmentRequestId\":\"request-1\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void localModuleInstallationGetAllowAndDenyAreExposed() throws Exception {
    Path artifact = jarArtifact("sea-module-local-1.2.3.jar");
    LocalArtifactInstallRequest request =
        localArtifactInstallations.create(module(), artifact.toString(), null);

    mockMvc
        .perform(get("/api/sea/local-module-installations/" + request.requestId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moduleId").value("sea-module-local"))
        .andExpect(jsonPath("$.status").value("pending"));

    mockMvc
        .perform(post("/api/sea/local-module-installations/" + request.requestId() + "/deny"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("denied"));

    mockMvc
        .perform(post("/api/sea/local-module-installations/" + request.requestId() + "/allow"))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/api/sea/local-module-installations/missing-request"))
        .andExpect(status().isNotFound());
  }

  @Test
  void developmentRequestAdminWorkflowIsExposed() throws Exception {
    String contractJson = contractJson();

    mockMvc
        .perform(
            post("/api/sea/development-requests")
                .contentType("application/json")
                .content(
                    "{\"contract\":" + contractJson + ",\"reason\":\"Build a fixture module\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.moduleId").value("sea-module-local"))
        .andExpect(jsonPath("$.status").value("prepared"));

    // Extract the request id from the persisted store deterministically.
    String id = latestDevelopmentRequestId();

    mockMvc
        .perform(get("/api/sea/development-requests/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moduleId").value("sea-module-local"));

    mockMvc
        .perform(
            post("/api/sea/development-requests/" + id + "/revisions")
                .contentType("application/json")
                .content(
                    "{\"contract\":" + contractJson + ",\"reason\":\"Revised fixture reason\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(2));

    mockMvc
        .perform(
            post("/api/sea/development-requests/" + id + "/exports")
                .contentType("application/json")
                .content("{\"workspaceRoot\":\"" + WORKSPACE.resolve("fixture-export") + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value(id));

    mockMvc
        .perform(post("/api/sea/development-requests/" + id + "/installation-approval"))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/api/sea/development-requests/missing-request"))
        .andExpect(status().isNotFound());
  }

  @Test
  void controlWorkspaceRendersRuntimeAndVerificationState() throws Exception {
    mockMvc
        .perform(get("/sea/control/workspace"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("test-module")))
        .andExpect(content().string(containsString("scoped-provider")))
        .andExpect(content().string(containsString("fixture-toolset")))
        .andExpect(content().string(containsString("Missing tools: absent-tool")))
        .andExpect(content().string(containsString("Provider is not loaded.")));
  }

  @Test
  void moduleReleaseInstallationLifecycleIsExposed() throws Exception {
    String requestId = createModuleReleaseInstallation();

    mockMvc
        .perform(get("/api/sea/module-release-installations/" + requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moduleId").value("sea-module-tika"))
        .andExpect(jsonPath("$.version").value("1.0.1"))
        .andExpect(jsonPath("$.status").value("pending"));

    mockMvc
        .perform(post("/api/sea/module-release-installations/" + requestId + "/allow"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"));
  }

  @Test
  void moduleReleaseInstallationDenyAndConflictOutcomesAreExposed() throws Exception {
    String requestId = createModuleReleaseInstallation();

    mockMvc
        .perform(post("/api/sea/module-release-installations/" + requestId + "/deny"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("denied"));

    mockMvc
        .perform(post("/api/sea/module-release-installations/" + requestId + "/allow"))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/api/sea/module-release-installations/missing-request"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/sea/module-release-installations")
                .contentType("application/json")
                .content(
                    "{\"moduleId\":\"unknown-module\",\"version\":\"1.0.1\",\"developmentRequestId\":null}"))
        .andExpect(status().isBadRequest());
  }

  private String createModuleReleaseInstallation() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/sea/module-release-installations")
                    .contentType("application/json")
                    .content(
                        "{\"moduleId\":\"sea-module-tika\",\"version\":\"1.0.1\",\"developmentRequestId\":null}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.moduleId").value("sea-module-tika"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new tools.jackson.databind.ObjectMapper().readTree(body).get("requestId").asText();
  }

  private String latestDevelopmentRequestId() throws IOException {
    Path directory = WORKSPACE.resolve("module-development").resolve("requests");
    try (var paths = Files.list(directory)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(path -> path.getFileName().toString().replace(".json", ""))
          .sorted()
          .reduce((first, second) -> second)
          .orElseThrow(() -> new IllegalStateException("No development request was created"));
    }
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
      return new tools.jackson.databind.ObjectMapper().writeValueAsString(contract);

    } catch (Exception ex) {
      throw new IllegalStateException("Unable to serialize contract", ex);
    }
  }

  private Path jarArtifact(String name) throws IOException {
    Path jar = WORKSPACE.resolve(name);
    Files.writeString(jar, "fixture module artifact");
    return jar;
  }

  private static SourceModuleIndex.Module module() {
    return new SourceModuleIndex.Module(
        "sea-module-local",
        "1.2.3",
        "Local Fixture",
        "Locally developed fixture module",
        java.net.URI.create("https://github.com/example/sea-module-local"),
        new SourceModuleIndex.Artifact("ai.sea.modules", "sea-module-local", "1.2.3"),
        null,
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of("type", "object"),
        List.of(new SourceModuleIndex.Factory("local-factory", "test")),
        List.of(
            new SourceModuleIndex.Operation("lookup", "Lookup", false, Map.of("type", "object"))),
        new SourceModuleIndex.Security(List.of()));
  }

  private static SeaProvider scopedProvider() {
    SeaProvider provider = mock(SeaProvider.class);
    ProviderDescriptor descriptor =
        new ProviderDescriptor(
            "scoped-provider",
            "test-module",
            "test",
            "Scoped Provider",
            "Test provider",
            "1",
            ProviderCapabilities.toolsOnly(),
            List.of("sea_backed"),
            Map.of("owner", "self"));
    when(provider.descriptor()).thenReturn(descriptor);
    when(provider.capabilities()).thenReturn(ProviderCapabilities.toolsOnly());
    when(provider.listTools())
        .thenReturn(
            List.of(
                new SeaToolDescriptor(
                    "write",
                    "Writes scoped data",
                    true,
                    List.of("member-safe"),
                    Map.of("type", "object"))));
    when(provider.listResources())
        .thenReturn(List.of(new ResourceDescriptor("sea://scoped/example", "Example resource")));
    when(provider.listPrompts())
        .thenReturn(List.of(new PromptDescriptor("example", "Example prompt")));
    when(provider.readResource(
            org.mockito.ArgumentMatchers.eq("sea://scoped/example"),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(SeaOperationResult.success(Map.of("content", "example")));
    when(provider.resolvePrompt(
            org.mockito.ArgumentMatchers.eq("example"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(SeaOperationResult.success(Map.of("prompt", "resolved")));
    when(provider.callTool(
            org.mockito.ArgumentMatchers.eq("write"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(SeaOperationResult.success(Map.of("written", true)));
    return provider;
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-admin-rest-test-");
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
  static class RuntimeTestConfiguration {

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
      return (uri, token) ->
          new ModuleReleaseIndex(
              1,
              "sea-module-tika",
              List.of(
                  new ModuleReleaseIndex.Release(
                      "1.0.1",
                      "v1.0.1",
                      new ModuleReleaseIndex.Artifact(
                          "org.example", "module", "1.0.1", fixtureDigest()),
                      new ModuleReleaseIndex.Source(
                          URI.create("https://github.com/example/module"), "Apache-2.0"),
                      new ModuleReleaseIndex.Compatibility(">=1.0.0"),
                      new ModuleReleaseIndex.Security(List.of()))));
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

    @Bean
    @Primary
    SeaRuntime loadedSeaRuntime() {
      SeaModule module =
          new SeaModule() {
            @Override
            public ModuleDescriptor descriptor() {
              return new ModuleDescriptor("test-module", "1.0.0", "Test Module", "Test module.");
            }

            @Override
            public List<org.zalava.ProviderFactory> providerFactories() {
              return List.of();
            }

            @Override
            public List<SeaVerificationContributor> verificationContributors() {
              return List.of(
                  () ->
                      List.of(
                          new SeaVerificationDescriptor(
                              "fixture-toolset",
                              "scoped-provider",
                              List.of("write"),
                              List.of(
                                  new SeaVerificationStep(
                                      "Write check",
                                      "POST",
                                      "/api/sea/providers/scoped-provider/tools/write/invoke",
                                      "write",
                                      true,
                                      true,
                                      Map.of("type", "object")))),
                          new SeaVerificationDescriptor(
                              "missing-tool-toolset",
                              "scoped-provider",
                              List.of("write", "absent-tool"),
                              List.of()),
                          new SeaVerificationDescriptor(
                              "unloaded-toolset", "missing-provider", List.of("run"), List.of())));
            }
          };
      ProviderFactoryDescriptor factory =
          new ProviderFactoryDescriptor("local-factory", "test-module", "test", "Test", "Test.");
      return new SeaRuntime() {
        @Override
        public List<SeaModule> modules() {
          return List.of(module);
        }

        @Override
        public List<LoadedSeaProvider> loadedProviders() {
          return List.of(new LoadedSeaProvider(module.descriptor(), factory, scopedProvider()));
        }

        @Override
        public void close() {}
      };
    }
  }
}
