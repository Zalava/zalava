package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;
import org.zalava.support.ComponentTestAccounts;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  AuthenticatedMockMvcTestConfiguration.class,
  ModulesControllerComponentTest.ChatModelTestConfiguration.class
})
class ActuatorRestartComponentTest {
  private static final Path WORKSPACE = workspace();
  @Autowired MockMvc mvc;
  @Autowired WebApplicationContext context;
  @Autowired ComponentTestAccounts accounts;
  @MockitoBean RestartEndpoint endpoint;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    r.add("zalava.managed-restart.dispatcher-enabled", () -> "false");
    r.add("management.server.port", () -> "8080");
    r.add("management.endpoint.restart.enabled", () -> "true");
    r.add("management.endpoints.web.exposure.include", () -> "health,restart");
    r.add("zalava.accounts.security-enabled", () -> "true");
    r.add("zalava.accounts.bootstrap-login", () -> "restart-admin");
    r.add("zalava.accounts.bootstrap-password", () -> "RestartPassword-123");
    r.add("spring.ai.model.chat", () -> "none");
    r.add("jobrunr.background-job-server.enabled", () -> "false");
    r.add("jobrunr.dashboard.enabled", () -> "false");
    r.add(
        "spring.allConfig.location",
        () -> WORKSPACE.resolve("private/application.private.yaml").toString());
  }

  @BeforeEach
  void resetFixture() throws Exception {
    reset(endpoint);
    when(endpoint.isRunning()).thenReturn(true);
    when(endpoint.restart()).thenReturn(Map.of("message", "Restarting"));
    Path journal =
        WORKSPACE
            .resolveSibling(WORKSPACE.getFileName() + "-application-control")
            .resolve("zalava-runtime-restart");
    Files.deleteIfExists(journal.resolve("request.properties"));
    Files.deleteIfExists(journal.resolve("failure"));
  }

  @Test
  void offersApplicationRestartWithoutServiceManagerAndDeduplicatesRequests() throws Exception {
    mvc.perform(get("/modules"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Ready to restart the Zalava application")))
        .andExpect(content().string(containsString("data-action=\"restart-zalava\"")));
    mvc.perform(post("/modules/restart"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("marketplaceMessage", containsString("Restart requested")));
    mvc.perform(post("/modules/restart")).andExpect(status().is3xxRedirection());
    verify(endpoint, times(1)).restart();
    assertThat(WORKSPACE.resolve("zalava-runtime-restart/request.properties")).doesNotExist();
    mvc.perform(get("/modules"))
        .andExpect(content().string(containsString("data-action=\"restart-zalava\" disabled")));
  }

  @Test
  void unavailableEndpointDoesNotAcceptRequest() throws Exception {
    when(endpoint.isRunning()).thenReturn(false);
    mvc.perform(post("/modules/restart"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("marketplaceMessage", containsString("unavailable")));
    verify(endpoint, never()).restart();
  }

  @Test
  void synchronousFailureIsVisibleAndCanBeRetried() throws Exception {
    when(endpoint.restart())
        .thenThrow(new IllegalStateException("sensitive implementation detail"));
    mvc.perform(post("/modules/restart"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("marketplaceError", containsString("restart failed")));
    mvc.perform(get("/modules"))
        .andExpect(content().string(containsString("data-zalava-restart-status=\"FAILED\"")));
    doReturn(Map.of("message", "Restarting")).when(endpoint).restart();
    mvc.perform(post("/modules/restart")).andExpect(flash().attributeExists("marketplaceMessage"));
    verify(endpoint, times(2)).restart();
  }

  @Test
  void protectsUiAndDirectActuatorEntryPointsWithRoleAndCsrf() throws Exception {
    MockMvc secured = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    var admin = accounts.newActivated(AccountRole.ADMIN);
    var member = accounts.newActivated(AccountRole.MEMBER);
    for (String url : new String[] {"/modules/restart", "/actuator/restart"}) {
      secured.perform(post(url)).andExpect(status().isForbidden());
      secured
          .perform(post(url).with(accounts.authenticatedAs(admin)))
          .andExpect(status().isForbidden());
      secured
          .perform(post(url).with(accounts.authenticatedAs(member)).with(csrf()))
          .andExpect(status().isForbidden());
    }
    secured
        .perform(post("/actuator/restart").with(accounts.authenticatedAs(admin)).with(csrf()))
        .andExpect(status().isOk());
    verify(endpoint).restart();
  }

  private static Path workspace() {
    try {
      Path p = Files.createTempDirectory("actuator-restart-component-");
      Files.writeString(p.resolve("AGENT.md"), "Disposable restart test.");
      return p;
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }
}
