package org.zalava.skills.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.support.AuthenticatedSeaComponentTest;
import org.zalava.support.ComponentTestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context MockMvc component test for the read-only skill discovery surface. It drives the real
 * controller, actor resolver and filesystem catalogue against the component workspace's maintained
 * {@code SKILL.md}, so ownership/policy visibility and bounded discovery are proven over real HTTP.
 */
@AuthenticatedSeaComponentTest
class SkillControllerComponentTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ComponentTestAccounts accounts;

  @Test
  void authenticatedMemberDiscoversInstalledSkillMetadata() throws Exception {
    Account member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(get("/api/skills").with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("test-skill"))
        .andExpect(jsonPath("$[0].status").value("INSTALLED"))
        .andExpect(jsonPath("$[0].provenanceSource").value("local"));

    mockMvc
        .perform(get("/api/skills/test-skill").with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Minimal component test skill."));
  }

  @Test
  void searchIsBoundedAndUnknownSkillsAreNotFound() throws Exception {
    Account member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(
            get("/api/skills/search")
                .param("query", "skill")
                .param("limit", "1")
                .with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(get("/api/skills/not-a-skill").with(accounts.authenticatedAs(member)))
        .andExpect(status().isNotFound());
  }

  @Test
  void activeAndRemoteListsAreEmptyWithoutConfiguration() throws Exception {
    Account admin = accounts.newActivated(AccountRole.ADMIN);

    assertThat(
            mockMvc
                .perform(get("/api/skills/active").with(accounts.authenticatedAs(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .isEqualTo("[]");
    mockMvc
        .perform(get("/api/skills/remote").with(accounts.authenticatedAs(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
