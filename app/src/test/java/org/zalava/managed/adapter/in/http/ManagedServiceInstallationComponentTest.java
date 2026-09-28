package org.zalava.managed.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
  ManagedServiceInstallationComponentTest.InstallTestConfiguration.class,
  AuthenticatedMockMvcTestConfiguration.class
})
@WithMockUser(username = "managed-install-admin", roles = "ADMIN")
class ManagedServiceInstallationComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeRuntimeEngine runtimeEngine;
  @Autowired private ManagedServiceStateStore states;
  @Autowired private org.zalava.accounts.application.port.in.AccountLifecycle accounts;

  @BeforeEach
  void enableBootstrapAdministrator() {
    var administrator =
        accounts
            .findByLoginName("managed-install-admin")
            .orElseGet(
                () ->
                    accounts.create(
                        "managed-install-admin",
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
    for (String directory : List.of("managed-services", "managed-install-requests")) {
      Path target = WORKSPACE.resolve(directory);
      if (!Files.isDirectory(target)) continue;
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
    registry.add("sea.accounts.bootstrap-login", () -> "managed-install-admin");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void planAllowAndStatusFlowStartsServicesThroughTheModuleEngineBridge() throws Exception {
    String requestId = plan("database", "app");

    mockMvc
        .perform(get("/api/sea/managed-service-installations/" + requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"))
        .andExpect(jsonPath("$.aggregate.totalProcessLimit").value(2))
        .andExpect(jsonPath("$.aggregate.dataPaths.length()").value(2));

    mockMvc
        .perform(post("/api/sea/managed-service-installations/" + requestId + "/allow"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"));

    assertThat(states.find("database"))
        .hasValueSatisfying(
            record ->
                assertThat(record.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING));
    assertThat(states.find("app"))
        .hasValueSatisfying(
            record ->
                assertThat(record.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING));
    assertThat(runtimeEngine.created).containsExactly("database", "app");
    assertThat(runtimeEngine.started).containsExactly("database", "app");
  }

  @Test
  void denialRecordsNoExecutionAndAllowAfterDenyConflicts() throws Exception {
    String requestId = plan("database");

    mockMvc
        .perform(post("/api/sea/managed-service-installations/" + requestId + "/deny"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("denied"));

    mockMvc
        .perform(post("/api/sea/managed-service-installations/" + requestId + "/allow"))
        .andExpect(status().isConflict());

    assertThat(states.find("database")).isEmpty();
    assertThat(runtimeEngine.created).isEmpty();
  }

  @Test
  void cycleAndDuplicatePlanningAreRejectedAsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/sea/managed-service-installations")
                .contentType("application/json")
                .content(cycleJson()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/sea/managed-service-installations")
                .contentType("application/json")
                .content(duplicateJson()))
        .andExpect(status().isBadRequest());

    assertThat(runtimeEngine.created).isEmpty();
  }

  @Test
  void conflictAndNotFoundOutcomesAreExposed() throws Exception {
    String requestId = plan("database");
    mockMvc
        .perform(post("/api/sea/managed-service-installations/" + requestId + "/allow"))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/sea/managed-service-installations/" + requestId + "/allow"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/sea/managed-service-installations/missing-request"))
        .andExpect(status().isNotFound());
  }

  private String plan(String... serviceIdsInDependencyOrder) throws Exception {
    StringBuilder services = new StringBuilder();
    Set<String> alreadyPlanned = new LinkedHashSet<>();
    for (String serviceId : serviceIdsInDependencyOrder) {
      if (!services.isEmpty()) services.append(',');
      services.append(serviceJson(serviceId, String.join("\",\"", alreadyPlanned)));
      alreadyPlanned.add(serviceId);
    }
    String response =
        mockMvc
            .perform(
                post("/api/sea/managed-service-installations")
                    .contentType("application/json")
                    .content("{\"requests\":[" + services + "]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(response)
        .get("requestId")
        .asText();
  }

  private static String serviceJson(String serviceId, String dependencies) {
    String dependsOn = dependencies.isBlank() ? "" : "\"" + dependencies + "\"";
    return "{\"moduleId\":\"home-module\",\"serviceId\":\""
        + serviceId
        + "\",\"dependsOn\":["
        + dependsOn
        + "],\"desiredState\":{\"resourceId\":\""
        + serviceId
        + "\",\"artifactReference\":\"registry.example/"
        + serviceId
        + "@sha256:"
        + "a".repeat(64)
        + "\",\"revision\":\"1\",\"lifecycle\":\"RUNNING\",\"secretReferences\":[],\"dataPaths\":[\"/var/lib/sea/managed/"
        + serviceId
        + "\"],\"ports\":[],\"devices\":[],\"limits\":{\"cpuMillis\":1000,\"memoryBytes\":10,\"processLimit\":1},\"readinessDeadline\":\"PT30S\",\"restartLimit\":3},"
        + "\"grant\":{\"moduleId\":\"home-module\",\"secretReferences\":[],\"dataPaths\":[\"/var/lib/sea/managed/"
        + serviceId
        + "\"],\"ports\":[],\"devices\":[],\"limits\":{\"cpuMillis\":1000,\"memoryBytes\":10,\"processLimit\":1},\"maximumReadinessDeadline\":\"PT30S\",\"maximumRestartLimit\":3}}";
  }

  private static String cycleJson() {
    return "{\"requests\":["
        + serviceJson("app", "database")
        + ","
        + serviceJson("database", "app")
        + "]}";
  }

  private static String duplicateJson() {
    return "{\"requests\":["
        + serviceJson("database", "")
        + ","
        + serviceJson("database", "")
        + "]}";
  }

  private static Path createWorkspace() {
    try {
      return Files.createTempDirectory("sea-managed-install-test");
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @TestConfiguration
  static class InstallTestConfiguration {

    @Bean
    @Primary
    OciServiceEngine fakeRuntimeEngine() {
      return new FakeRuntimeEngine();
    }
  }

  static final class FakeRuntimeEngine implements OciServiceEngine {
    final Set<String> created = new LinkedHashSet<>();
    final Set<String> started = new LinkedHashSet<>();
    final Map<String, String> owners = new HashMap<>();

    void reset() {
      created.clear();
      started.clear();
      owners.clear();
    }

    @Override
    public Observation inspect(String serviceId) {
      boolean exists = owners.containsKey(serviceId);
      return new Observation(
          exists,
          started.contains(serviceId),
          exists && started.contains(serviceId),
          owners.get(serviceId),
          exists ? "managed-" + serviceId : null);
    }

    @Override
    public void create(ManagedServiceRecord record) {
      created.add(record.serviceId());
      owners.put(record.serviceId(), record.grant().moduleId());
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
      owners.remove(serviceId);
      started.remove(serviceId);
    }

    @Override
    public List<String> recentLogs(String serviceId, int maxLines) {
      return List.of();
    }
  }
}
