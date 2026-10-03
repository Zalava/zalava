package org.zalava.web.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.AuthenticatedZalavaComponentTest;
import org.zalava.support.ComponentTestAccounts;

@AuthenticatedZalavaComponentTest
class ZalavaLogsControllerComponentTest {
  private static final Path LOG =
      Path.of(
          System.getProperty("java.io.tmpdir"), "zalava-monitoring-" + UUID.randomUUID() + ".log");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("logging.file.name", LOG::toString);
  }

  @Autowired MockMvc mockMvc;
  @Autowired ComponentTestAccounts accounts;

  @Test
  void administratorCanReadLogsFromMonitoring() throws Exception {
    Files.writeString(LOG, "Zalava started\n");
    try {
      mockMvc
          .perform(get("/monitoring"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("href=\"/monitoring/logs\"")));
      mockMvc
          .perform(get("/monitoring/logs"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("Zalava started")));
    } finally {
      Files.deleteIfExists(LOG);
    }
  }

  @Test
  void onlyAdministratorCanReadLogs() throws Exception {
    var member = accounts.newActivated(AccountRole.MEMBER);
    mockMvc
        .perform(get("/monitoring/logs").with(accounts.authenticatedAs(member)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/monitoring/logs").with(anonymous()))
        .andExpect(status().is3xxRedirection());
  }
}
