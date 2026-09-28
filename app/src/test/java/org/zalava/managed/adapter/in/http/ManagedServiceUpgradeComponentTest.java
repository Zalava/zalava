package org.zalava.managed.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.zalava.managed.application.ManagedServiceObservedState;
import org.zalava.managed.application.ManagedServiceRecord;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;
import org.zalava.managed.application.port.out.OciServiceEngine;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;
import org.zalava.support.PostgreSqlTestDatabase;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  ManagedServiceUpgradeComponentTest.UpgradeTestConfiguration.class,
  AuthenticatedMockMvcTestConfiguration.class
})
@WithMockUser(username = "managed-upgrade-admin", roles = "ADMIN")
class ManagedServiceUpgradeComponentTest {

  private static final Path WORKSPACE = createWorkspace();
  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeRuntimeEngine runtimeEngine;
  @Autowired private ManagedServiceStateStore states;
  @Autowired private org.zalava.accounts.application.port.in.AccountLifecycle accounts;

  @BeforeEach
  void enableBootstrapAdministrator() {
    var administrator =
        accounts
            .findByLoginName("managed-upgrade-admin")
            .orElseGet(
                () ->
                    accounts.create(
                        "managed-upgrade-admin",
                        "TestBootstrapPassword-123",
                        org.zalava.accounts.domain.AccountRole.ADMIN));
    if (administrator.passwordChangeRequired()) {
      accounts.changePassword(
          administrator.id(), "TestBootstrapPassword-123", "AdministratorPassword-123");
    }
    runtimeEngine.reset();
    clearStores();
  }

  private static void clearStores() {
    for (String directory :
        List.of(
            "managed-services",
            "managed-install-requests",
            "managed-upgrade-requests",
            "managed-backups")) {
      deleteTree(WORKSPACE.resolve(directory));
    }
  }

  private static void deleteTree(Path target) {
    if (!Files.isDirectory(target)) return;
    try (var paths = Files.walk(target)) {
      paths
          .skip(1)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (java.io.IOException ex) {
                  throw new IllegalStateException(ex);
                }
              });
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    PostgreSqlTestDatabase.register(registry);
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("sea.accounts.bootstrap-login", () -> "managed-upgrade-admin");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void approvedUpgradePromotesTheCandidateAndDiagnosticsExposeBoundedState() throws Exception {
    seedInstalledService("database");
    String requestId = planUpgrade("database", "2");
    long generation = generationOf(requestId);

    mockMvc
        .perform(get("/api/sea/managed-service-upgrades/" + requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"))
        .andExpect(jsonPath("$.services[0].previousRevision").value("1"))
        .andExpect(jsonPath("$.services[0].candidateRevision").value("2"));

    mockMvc
        .perform(
            post("/api/sea/managed-service-upgrades/" + requestId + "/allow")
                .param("generation", String.valueOf(generation)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"))
        .andExpect(jsonPath("$.services[0].phase").value("PROMOTED"));

    assertThat(states.find("database"))
        .hasValueSatisfying(
            record -> {
              assertThat(record.desiredState().revision()).isEqualTo("2");
              assertThat(record.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING);
            });
    assertThat(runtimeEngine.removals).containsExactly("database");

    mockMvc
        .perform(get("/api/sea/managed-services/database"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceId").value("database"))
        .andExpect(jsonPath("$.observedState").value("RUNNING"))
        .andExpect(jsonPath("$.desiredRevision").value("2"));

    mockMvc
        .perform(get("/api/sea/managed-services/database/logs").param("lines", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("line-1"));

    mockMvc
        .perform(post("/api/sea/managed-services/database/restart"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.observedState").value("RUNNING"));
  }

  @Test
  void failedCandidateReadinessAutomaticallyRollsBackToThePreviousRevision() throws Exception {
    seedInstalledService("database");
    runtimeEngine.readyRevisions.clear();
    runtimeEngine.readyRevisions.add("1");
    String requestId = planUpgrade("database", "2");
    long generation = generationOf(requestId);

    mockMvc
        .perform(
            post("/api/sea/managed-service-upgrades/" + requestId + "/allow")
                .param("generation", String.valueOf(generation)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("rolled_back"))
        .andExpect(jsonPath("$.services[0].phase").value("ROLLED_BACK"));

    assertThat(states.find("database"))
        .hasValueSatisfying(
            record -> {
              assertThat(record.desiredState().revision()).isEqualTo("1");
              assertThat(record.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING);
            });
  }

  @Test
  void staleGenerationDenyAndUnknownSurfacesAreRejected() throws Exception {
    seedInstalledService("database");
    String requestId = planUpgrade("database", "2");

    mockMvc
        .perform(
            post("/api/sea/managed-service-upgrades/" + requestId + "/deny")
                .param("generation", "99"))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/api/sea/managed-service-upgrades/missing-request"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/sea/managed-services/ghost-service"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(post("/api/sea/managed-services/ghost-service/restart"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/sea/managed-service-upgrades")
                .contentType("application/json")
                .content(upgradeJson("ghost-service", "2")))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/sea/managed-service-upgrades/" + requestId + "/deny")
                .param("generation", String.valueOf(generationOf(requestId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("denied"));
  }

  private void seedInstalledService(String serviceId) throws Exception {
    Path dataPath = WORKSPACE.resolve("data").resolve(serviceId);
    Files.createDirectories(dataPath);
    String response =
        mockMvc
            .perform(
                post("/api/sea/managed-service-installations")
                    .contentType("application/json")
                    .content("{\"requests\":[" + installServiceJson(serviceId) + "]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String installRequestId = JSON.readTree(response).get("requestId").asText();
    mockMvc
        .perform(post("/api/sea/managed-service-installations/" + installRequestId + "/allow"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"));
  }

  private String planUpgrade(String serviceId, String candidateRevision) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/sea/managed-service-upgrades")
                    .contentType("application/json")
                    .content(upgradeJson(serviceId, candidateRevision)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(response).get("requestId").asText();
  }

  private long generationOf(String requestId) throws Exception {
    String response =
        mockMvc
            .perform(get("/api/sea/managed-service-upgrades/" + requestId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(response).get("generation").asLong();
  }

  private static String dataPathFor(String serviceId) {
    return WORKSPACE.resolve("data").resolve(serviceId).toString();
  }

  private static String installServiceJson(String serviceId) {
    return "{\"moduleId\":\"home-module\",\"serviceId\":\""
        + serviceId
        + "\",\"dependsOn\":[],\"desiredState\":{\"resourceId\":\""
        + serviceId
        + "\",\"artifactReference\":\"registry.example/"
        + serviceId
        + "@sha256:"
        + "a".repeat(64)
        + "\",\"revision\":\"1\",\"lifecycle\":\"RUNNING\",\"secretReferences\":[],\"dataPaths\":[\""
        + dataPathFor(serviceId)
        + "\"],\"ports\":[],\"devices\":[],\"limits\":{\"cpuMillis\":1000,\"memoryBytes\":10,\"processLimit\":1},\"readinessDeadline\":\"PT30S\",\"restartLimit\":3},"
        + "\"grant\":{\"moduleId\":\"home-module\",\"secretReferences\":[],\"dataPaths\":[\""
        + dataPathFor(serviceId)
        + "\"],\"ports\":[],\"devices\":[],\"limits\":{\"cpuMillis\":1000,\"memoryBytes\":10,\"processLimit\":1},\"maximumReadinessDeadline\":\"PT30S\",\"maximumRestartLimit\":3}}";
  }

  private static String upgradeJson(String serviceId, String candidateRevision) {
    String digest = "2".equals(candidateRevision) ? "b".repeat(64) : "a".repeat(64);
    return "{\"candidates\":[{\"moduleId\":\"home-module\",\"serviceId\":\""
        + serviceId
        + "\",\"candidateState\":{\"resourceId\":\""
        + serviceId
        + "\",\"artifactReference\":\"registry.example/"
        + serviceId
        + "@sha256:"
        + digest
        + "\",\"revision\":\""
        + candidateRevision
        + "\",\"lifecycle\":\"RUNNING\",\"secretReferences\":[],\"dataPaths\":[\""
        + dataPathFor(serviceId)
        + "\"],\"ports\":[],\"devices\":[],\"limits\":{\"cpuMillis\":1000,\"memoryBytes\":10,\"processLimit\":1},\"readinessDeadline\":\"PT30S\",\"restartLimit\":3}}]}";
  }

  private static Path createWorkspace() {
    try {
      return Files.createTempDirectory("sea-managed-upgrade-test");
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @TestConfiguration
  static class UpgradeTestConfiguration {

    @Bean
    @Primary
    OciServiceEngine fakeRuntimeEngine() {
      return new FakeRuntimeEngine();
    }
  }

  static final class FakeRuntimeEngine implements OciServiceEngine {

    final Set<String> created = new HashSet<>();
    final Set<String> started = new HashSet<>();
    final Map<String, String> revisions = new HashMap<>();
    final Set<String> removals = new HashSet<>();
    final Set<String> readyRevisions = new HashSet<>(List.of("1", "2"));

    void reset() {
      created.clear();
      started.clear();
      revisions.clear();
      removals.clear();
      readyRevisions.clear();
      readyRevisions.add("1");
      readyRevisions.add("2");
    }

    @Override
    public Observation inspect(String serviceId) {
      boolean exists = created.contains(serviceId);
      boolean running = started.contains(serviceId);
      boolean ready = exists && running && readyRevisions.contains(revisions.get(serviceId));
      return new Observation(exists, running, ready, "home-module", "managed-" + serviceId);
    }

    @Override
    public void create(ManagedServiceRecord record) {
      created.add(record.serviceId());
      started.add(record.serviceId());
      revisions.put(record.serviceId(), record.desiredRevision());
    }

    @Override
    public void start(String serviceId) {
      started.add(serviceId);
    }

    @Override
    public void stop(String serviceId) {
      started.remove(serviceId);
    }

    @Override
    public void remove(String serviceId) {
      removals.add(serviceId);
      created.remove(serviceId);
      started.remove(serviceId);
    }

    @Override
    public List<String> recentLogs(String serviceId, int maxLines) {
      return List.of("line-1", "line-2", "line-3");
    }
  }
}
