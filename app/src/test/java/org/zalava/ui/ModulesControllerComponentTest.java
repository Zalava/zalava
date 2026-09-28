package org.zalava.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.zalava.ManagedServiceDeclaration;
import org.zalava.ModuleConfigurationDescriptor;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.SeaModule;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.catalog.FileSystemModuleConfigurationStore;
import org.zalava.catalog.ModuleConfigurationSnapshot;
import org.zalava.catalog.install.application.port.in.LocalDevelopmentProjectInstallation;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;
import org.zalava.managed.application.ManagedServiceObservedState;
import org.zalava.managed.application.ManagedServiceRecord;
import org.zalava.managed.application.port.out.ManagedServiceInstallRequestStore;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;
import org.zalava.runtime.LoadedSeaProvider;
import org.zalava.runtime.SeaRuntime;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;
import org.zalava.support.ComponentTestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  AuthenticatedMockMvcTestConfiguration.class,
  ModulesControllerComponentTest.ChatModelTestConfiguration.class,
  ModulesControllerComponentTest.LocalDevelopmentProjectInstallationTestConfiguration.class,
  ModulesControllerComponentTest.ConfiguredRuntimeTestConfiguration.class
})
class ModulesControllerComponentTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;
  @Autowired private ComponentTestAccounts accounts;

  @Autowired private FileSystemModuleConfigurationStore moduleConfigurationStore;

  @Autowired private ManagedServiceStateStore managedServiceStateStore;

  @Autowired private ManagedServiceInstallRequestStore managedServiceInstallRequestStore;

  @Autowired private CapturingLocalDevelopmentProjectInstallation localDevelopmentProjects;

  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("sea.managed-restart.dispatcher-enabled", () -> "true");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> "modules-test");
    registry.add("sea.accounts.bootstrap-password", () -> "ModulesTestPassword-123");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "none");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add(
        "spring.allConfig.location",
        () -> WORKSPACE.resolve("private/application.private.yaml").toString());
  }

  @BeforeEach
  void setUp() throws IOException {
    localDevelopmentProjects.reset();
    for (Path root :
        List.of(
            WORKSPACE.resolve("source-module-installation"),
            WORKSPACE.resolve("private"),
            WORKSPACE.resolve("managed-services"),
            WORKSPACE.resolve("managed-install-requests"),
            WORKSPACE.getParent().resolve("module-configuration"))) {
      if (Files.exists(root)) {
        try (var paths = Files.walk(root)) {
          paths.sorted(Comparator.reverseOrder()).forEach(ModulesControllerComponentTest::delete);
        }
      }
    }
    Files.writeString(WORKSPACE.resolve("AGENT.md"), "Test agent prompt.");
  }

  @Test
  void rendersLoadedModulesAndNavigation() throws Exception {
    mockMvc
        .perform(get("/modules"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>SEA Modules</title>")))
        .andExpect(
            content()
                .string(
                    containsString(
                        "class=\"navbar-item is-active\" aria-current=\"page\" href=\"/modules\"")))
        .andExpect(content().string(containsString("Loaded modules")))
        .andExpect(content().string(containsString("Built in")))
        .andExpect(content().string(containsString("Bundled with SEA")))
        .andExpect(content().string(containsString("data-metric=\"loaded-modules\"")))
        .andExpect(content().string(containsString("No external modules are enabled yet.")));
  }

  @Test
  void rejectsLocalDevelopmentInstallOutsideMountedWorkspace() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/modules/local-project-installations")
                .param("projectDirectory", "/not-mounted/module")
                .param("moduleId", "sea-module-example")
                .param("version", "1.0.0")
                .param("developmentRequestId", "development-1")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attributeExists("marketplaceError"));
  }

  @Test
  void preparesLocalDevelopmentInstallationFromModulesPage() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/modules/local-project-installations")
                .param("projectDirectory", "/mounted/modules/sea-module-example")
                .param("moduleId", "sea-module-example")
                .param("version", "1.2.3")
                .param("developmentRequestId", "development-42")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            flash()
                .attribute(
                    "marketplaceMessage",
                    "Local module sea-module-example 1.2.3 installed. Restart SEA once to load its classes."));

    assertThat(localDevelopmentProjects.request())
        .isEqualTo(
            new LocalDevelopmentProjectInstallation.Request(
                "/mounted/modules/sea-module-example",
                "sea-module-example",
                "1.2.3",
                "development-42"));
  }

  @Test
  void preparesUploadedModuleInstallationFromModulesPage() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                    "/modules/upload-installations")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "moduleJar", "fixture.jar", "application/java-archive", uploadedJar()))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("marketplaceMessage", containsString("installed")));
  }

  @Test
  void rejectsInvalidUploadedModuleJarWithoutLeavingStagedArtifact() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                    "/modules/upload-installations")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "moduleJar",
                        "broken.jar",
                        "application/java-archive",
                        new byte[] {1, 2, 3}))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("marketplaceError", containsString("readable JAR")));

    Path uploads = WORKSPACE.resolve("source-module-installation/uploads");
    if (Files.exists(uploads)) {
      try (var entries = Files.list(uploads)) {
        assertThat(entries.toList()).isEmpty();
      }
    }
  }

  @Test
  void requestsManagedSeaRestartFromModulesPage() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/modules/restart")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("marketplaceMessage", containsString("Restart requested")));

    mockMvc
        .perform(get("/modules"))
        .andExpect(content().string(containsString("data-sea-restart-status=\"REQUESTED\"")))
        .andExpect(content().string(containsString("data-action=\"restart-sea\" disabled")));
  }

  @Test
  void rejectsManagedSeaRestartForMember() throws Exception {
    var member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/modules/restart")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .with(accounts.authenticatedAs(member)))
        .andExpect(status().isForbidden());
  }

  private static byte[] uploadedJar() throws IOException {
    try (java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(bytes)) {
      jar.putNextEntry(new java.util.jar.JarEntry("module-metadata.yaml"));
      jar.write(
          """
          schemaVersion: 1
          modules:
            - moduleId: uploaded-fixture
              version: 1.0.0
              displayName: Uploaded fixture
              description: Fixture
              supportUrl: https://example.test/uploaded-fixture
              artifact:
                groupId: org.zalava
                artifactId: uploaded-fixture
                version: 1.0.0
              compatibility:
                seaRuntime: ">=1.0.0"
              configurationSchema:
                type: object
              factories:
                - factoryId: fixture
                  providerType: fixture
              operations:
                - name: fixtureOperation
                  description: Fixture operation
                  sideEffecting: true
                  inputSchema:
                    type: object
              security:
                permissions: [file.read]
          """
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      jar.closeEntry();
      jar.finish();
      return bytes.toByteArray();
    }
  }

  @Test
  void rendersEnabledExternalModuleProvenance() throws Exception {
    Path artifact = enabledArtifact();
    String digest = "sha256:" + sha256(artifact);
    Path registry = WORKSPACE.resolve("source-module-installation/enabled-modules.json");
    Files.createDirectories(registry.getParent());
    JSON.writerWithDefaultPrettyPrinter()
        .writeValue(
            registry.toFile(),
            List.of(
                Map.of(
                    "moduleId", "sea-module-example",
                    "version", "1.2.3",
                    "artifactPath", artifact.toString(),
                    "artifactDigest", digest,
                    "seaRuntimeCompatibility", "[1.0,2.0)",
                    "sourceRepository", "https://github.com/Zalava/zalava-module-example",
                    "sourceLicense", "Apache-2.0",
                    "binaryRepositoryId", "local-private",
                    "declaredPermissions", List.of("filesystem:read"))));

    mockMvc
        .perform(get("/modules"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Enabled external modules")))
        .andExpect(content().string(containsString("data-metric=\"enabled-modules\">1<")))
        .andExpect(content().string(containsString("sea-module-example")))
        .andExpect(content().string(containsString("1.2.3")))
        .andExpect(content().string(containsString("https://github.com/Zalava/zalava-module-example")))
        .andExpect(content().string(containsString("Permissions")))
        .andExpect(content().string(containsString(">1</p>")));
  }

  @Test
  void rendersManagedServiceReadinessAndEndpoint() throws Exception {
    ManagedServiceDesiredState desired =
        new ManagedServiceDesiredState(
            "home-assistant",
            "ghcr.io/home-assistant/home-assistant@sha256:" + "a".repeat(64),
            "2026.9.1",
            ManagedServiceLifecycle.RUNNING,
            Set.of("home-assistant-token"),
            Set.of("/var/lib/sea/managed/home-assistant/config"),
            Set.of(8123),
            Set.of(),
            new ManagedServiceLimits(2000, 1_073_741_824, 256),
            Duration.ofMinutes(2),
            3);
    ManagedServiceResourceGrant grant =
        new ManagedServiceResourceGrant(
            "configured-search",
            Set.of("home-assistant-token"),
            Set.of("/var/lib/sea/managed/home-assistant/config"),
            Set.of(8123),
            Set.of(),
            new ManagedServiceLimits(2000, 1_073_741_824, 256),
            Duration.ofMinutes(2),
            3);
    managedServiceStateStore.save(
        new ManagedServiceRecord(
            "home-assistant",
            desired,
            "desired-1",
            grant,
            "grant-1",
            ManagedServiceObservedState.RUNNING,
            "rev-1",
            "data-1",
            0,
            null));

    mockMvc
        .perform(get("/modules/configured-search"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-managed-service=\"home-assistant\"")))
        .andExpect(content().string(containsString("data-managed-service-state>Running")))
        .andExpect(content().string(containsString("http://127.0.0.1:8123")))
        .andExpect(content().string(containsString("data-managed-service-endpoint")));
  }

  @Test
  void rendersDeclaredManagedServiceForDirectInstallation() throws Exception {
    mockMvc
        .perform(get("/modules/sea-module-declared"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-managed-service=\"declared-service\"")))
        .andExpect(content().string(containsString("Declared, awaiting administrator approval")))
        .andExpect(content().string(containsString("http://127.0.0.1:9000")))
        .andExpect(content().string(containsString("data-action=\"install-managed-service\"")));
  }

  @Test
  void requestsAnInstallForDeclaredManagedServices() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/modules/sea-module-declared/managed-services/request"))
        .andExpect(status().is3xxRedirection());

    assertThat(managedServiceInstallRequestStore.recent(1))
        .first()
        .satisfies(
            request -> {
              assertThat(request.status())
                  .isEqualTo(
                      org.zalava.managed.application.ManagedServiceInstallRequest.Status
                          .EXECUTING);
              assertThat(request.services())
                  .anySatisfy(
                      service -> assertThat(service.serviceId()).isEqualTo("declared-service"));
            });

    mockMvc
        .perform(get("/modules/sea-module-declared"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    containsString(
                        "data-managed-service-request-status>Install request status: EXECUTING")))
        .andExpect(content().string(containsString("data-action=\"install-managed-service\"")));
  }

  @Test
  void rejectsAnInstallRequestForAModuleWithoutDeclarations() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/modules/configured-search/managed-services/request"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attributeExists("managedServiceError"));
  }

  @Test
  void stagesSchemaDrivenConfigurationWithoutRenderingStoredSecretValues() throws Exception {
    moduleConfigurationStore.saveCandidate(
        new ModuleConfigurationSnapshot(
            "configured-search",
            "1.0.0",
            "schema-1",
            Map.of(
                "search",
                Map.of("endpoint", "https://old.example.test", "credentialRef", "search-key")),
            Map.of("search.credentialRef", "search-key")),
        Map.of("search-key", "actual-secret"));
    moduleConfigurationStore.promoteCandidate("configured-search");

    mockMvc
        .perform(get("/modules"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(containsString("data-module-detail-link=\"configured-search\"")));

    mockMvc
        .perform(get("/modules/configured-search"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("https://old.example.test")))
        .andExpect(content().string(containsString("Replace stored secret")))
        .andExpect(content().string(containsString("1 stored secret reference(s)")))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("actual-secret"))));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/modules/configured-search/configuration")
                .param("configuration.search.endpoint", "https://new.example.test")
                .param("configuration.search.credentialRef", "search-key")
                .param("replacement-secret.search.credentialRef", "replacement-secret"))
        .andExpect(status().is3xxRedirection());

    assertThat(moduleConfigurationStore.candidate("configured-search"))
        .hasValueSatisfying(
            snapshot ->
                assertThat(snapshot.factories())
                    .containsEntry(
                        "search",
                        Map.of(
                            "endpoint",
                            "https://new.example.test",
                            "credentialRef",
                            "search-key")));
    assertThat(
            moduleConfigurationStore
                .candidateSecrets("configured-search")
                .resolve("search-key")
                .map(String::new))
        .hasValue("replacement-secret");

    mockMvc
        .perform(get("/settings"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Configuration health")))
        .andExpect(
            content()
                .string(containsString("data-configuration-health-link=\"configured-search\"")))
        .andExpect(content().string(containsString("Changes pending")));
  }

  @Test
  void keepsAnInvalidConfigurationOnTheFormInsteadOfStagingIt() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/modules/configured-search/configuration")
                .param("configuration.search.credentialRef", "search-key"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("configurationError", "Endpoint is required."));

    assertThat(moduleConfigurationStore.candidate("configured-search")).isEmpty();
  }

  @Test
  void stagesStructuredConfigurationFromJsonTextArea() throws Exception {
    mockMvc
        .perform(get("/modules/configured-filesystem"))
        .andExpect(status().isOk())
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<textarea")))
        .andExpect(content().string(containsString("configuration.filesystem.roots")));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/modules/configured-filesystem/configuration")
                .param("configuration.filesystem.roots", "[{\"id\":\"workspace\"}]"))
        .andExpect(status().is3xxRedirection());

    assertThat(moduleConfigurationStore.candidate("configured-filesystem"))
        .hasValueSatisfying(
            snapshot ->
                assertThat(snapshot.factories())
                    .isEqualTo(
                        Map.of("filesystem", Map.of("roots", List.of(Map.of("id", "workspace"))))));
  }

  @Test
  void discardsPendingConfigurationAndRetainsTheActiveSnapshot() throws Exception {
    ModuleConfigurationSnapshot active =
        new ModuleConfigurationSnapshot(
            "configured-search",
            "1.0.0",
            "schema-1",
            Map.of(
                "search",
                Map.of("endpoint", "https://active.example.test", "credentialRef", "search-key")),
            Map.of("search.credentialRef", "search-key"));
    moduleConfigurationStore.saveCandidate(active, Map.of("search-key", "active-secret"));
    moduleConfigurationStore.promoteCandidate("configured-search");
    moduleConfigurationStore.saveCandidate(
        new ModuleConfigurationSnapshot(
            "configured-search",
            "1.0.0",
            "schema-1",
            Map.of(
                "search",
                Map.of("endpoint", "https://pending.example.test", "credentialRef", "search-key")),
            Map.of("search.credentialRef", "search-key")),
        Map.of("search-key", "pending-secret"));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/modules/configured-search/configuration/cancel"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            flash().attribute("configurationMessage", "Pending configuration changes discarded."));

    assertThat(moduleConfigurationStore.candidate("configured-search")).isEmpty();
    assertThat(moduleConfigurationStore.active("configured-search")).contains(active);
  }

  private static Path enabledArtifact() throws IOException {
    Path modulesRoot = WORKSPACE.resolve("source-module-installation/modules");
    Files.createDirectories(modulesRoot);
    Path artifact = modulesRoot.resolve("sea-module-example.jar");
    Files.writeString(artifact, "example module artifact");
    return artifact;
  }

  private static String sha256(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(Files.readAllBytes(path));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("modules-controller-component-test-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
      Files.writeString(
          skill.resolve("SKILL.md"),
          """
                    ---
                    name: test-skill
                    description: Minimal skill component test context startup.
                    ---
                    # Test Skill
                    """);
      return workspace;
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  private static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to clean modules test workspace", ex);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ChatModelTestConfiguration {

    @Bean
    @Primary
    ChatModel chatModel() {
      return new StaticChatModel();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class LocalDevelopmentProjectInstallationTestConfiguration {
    @Bean
    CapturingLocalDevelopmentProjectInstallation capturingLocalDevelopmentProjectInstallation() {
      return new CapturingLocalDevelopmentProjectInstallation();
    }

    @Bean
    @Primary
    LocalDevelopmentProjectInstallation testLocalDevelopmentProjectInstallation(
        CapturingLocalDevelopmentProjectInstallation installations) {
      return installations;
    }
  }

  static final class CapturingLocalDevelopmentProjectInstallation
      implements LocalDevelopmentProjectInstallation {
    private final AtomicReference<Request> request = new AtomicReference<>();

    @Override
    public org.zalava.catalog.LocalArtifactInstallRequest create(Request request) {
      if (!request.projectDirectory().startsWith("/mounted/modules/")) {
        throw new IllegalArgumentException("Built module project directory must be trusted");
      }
      this.request.set(request);
      return null;
    }

    Request request() {
      return request.get();
    }

    void reset() {
      request.set(null);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ConfiguredRuntimeTestConfiguration {

    @Bean
    @Primary
    SeaRuntime configuredSeaRuntime() {
      SeaModule filesystem =
          module("sea-filesystem", "SEA Filesystem", ModuleConfigurationDescriptor.none());
      SeaModule configuredSearch =
          module(
              "configured-search",
              "Configured Search",
              new ModuleConfigurationDescriptor(
                  Map.of(
                      "type",
                      "object",
                      "properties",
                      Map.of(
                          "search",
                          Map.of(
                              "type", "object",
                              "properties",
                                  Map.of(
                                      "endpoint", Map.of("type", "string", "title", "Endpoint"),
                                      "credentialRef",
                                          Map.of(
                                              "type", "string", "title", "Credential reference")),
                              "required", List.of("endpoint", "credentialRef"))))));
      SeaModule configuredFilesystem =
          module(
              "configured-filesystem",
              "Configured Filesystem",
              new ModuleConfigurationDescriptor(
                  Map.of(
                      "type",
                      "object",
                      "properties",
                      Map.of(
                          "filesystem",
                          Map.of(
                              "type", "object",
                              "properties", Map.of("roots", Map.of("type", "array")),
                              "required", List.of("roots"))))));
      SeaModule declaredModule = declaredModule();
      return new SeaRuntime() {
        @Override
        public List<SeaModule> modules() {
          return List.of(filesystem, configuredSearch, configuredFilesystem, declaredModule);
        }

        @Override
        public List<LoadedSeaProvider> loadedProviders() {
          return List.of();
        }

        @Override
        public void close() {}
      };
    }

    private static SeaModule module(
        String id, String name, ModuleConfigurationDescriptor configuration) {
      ModuleDescriptor descriptor = new ModuleDescriptor(id, "1.0.0", name, name + " module.");
      return new SeaModule() {
        @Override
        public ModuleDescriptor descriptor() {
          return descriptor;
        }

        @Override
        public List<ProviderFactory> providerFactories() {
          return List.of();
        }

        @Override
        public ModuleConfigurationDescriptor configuration() {
          return configuration;
        }
      };
    }

    private static SeaModule declaredModule() {
      ModuleDescriptor descriptor =
          new ModuleDescriptor(
              "sea-module-declared",
              "1.0.0",
              "Declared Provider",
              "Declares a managed service without an installation.");
      ManagedServiceDesiredState desired =
          new ManagedServiceDesiredState(
              "declared-service",
              "ghcr.io/example/declared@sha256:" + "b".repeat(64),
              "1.0.0",
              ManagedServiceLifecycle.RUNNING,
              Set.of(),
              Set.of("/var/lib/sea/managed/declared"),
              Set.of(9000),
              Set.of(),
              new ManagedServiceLimits(500, 268_435_456, 64),
              Duration.ofMinutes(1),
              2);
      ManagedServiceDeclaration declaration =
          new ManagedServiceDeclaration("declared-service", desired);
      return new SeaModule() {
        @Override
        public ModuleDescriptor descriptor() {
          return descriptor;
        }

        @Override
        public List<ProviderFactory> providerFactories() {
          return List.of();
        }

        @Override
        public List<ManagedServiceDeclaration> managedServices() {
          return List.of(declaration);
        }
      };
    }
  }

  private static final class StaticChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
      return new ChatResponse(List.of(new Generation(new AssistantMessage("unused"))));
    }

    @Override
    public ChatOptions getOptions() {
      return ChatOptions.builder().build();
    }
  }
}
