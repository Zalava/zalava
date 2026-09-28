package org.zalava.skills.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.support.AuthenticatedSeaComponentTest;
import org.zalava.support.ComponentTestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context MockMvc component test for skill activation. It drives the real controller, actor
 * resolver, catalogue, content source and activation store against the component workspace, so
 * policy denial, rollback and stale-version handling are proven over real HTTP.
 */
@AuthenticatedSeaComponentTest
class SkillActivationControllerComponentTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ComponentTestAccounts accounts;

  @Test
  void memberActivatesAndRollsBackASkill() throws Exception {
    Account member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(post("/api/skills/test-skill/activate").with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("test-skill"))
        .andExpect(jsonPath("$.state").value("ACTIVE"))
        .andExpect(jsonPath("$.version").value("0.0.0"));

    mockMvc
        .perform(get("/api/skills/activations").with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("test-skill"));

    mockMvc
        .perform(get("/api/skills/activations/test-skill").with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ACTIVE"));

    mockMvc
        .perform(post("/api/skills/test-skill/deactivate").with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("DEACTIVATED"));

    mockMvc
        .perform(get("/api/skills/activations").with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void deniesAnAdminOnlySkillToAMember() throws Exception {
    Account member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(post("/api/skills/admin-skill/activate").with(accounts.authenticatedAs(member)))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowsAnAdminOnlySkillToAnAdministrator() throws Exception {
    Account admin = accounts.newActivated(AccountRole.ADMIN);

    mockMvc
        .perform(post("/api/skills/admin-skill/activate").with(accounts.authenticatedAs(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ACTIVE"));
  }

  @Test
  void rejectsAStaleVersionPin() throws Exception {
    Account member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(
            post("/api/skills/test-skill/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"9.9.9\"}")
                .with(accounts.authenticatedAs(member)))
        .andExpect(status().isConflict());
  }

  @Test
  void unknownSkillsAndActivationsAreNotFound() throws Exception {
    Account member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(post("/api/skills/ghost-skill/activate").with(accounts.authenticatedAs(member)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/skills/activations/ghost-skill").with(accounts.authenticatedAs(member)))
        .andExpect(status().isNotFound());
  }

  @Test
  void activationIsBoundedToTheRequestingActor() throws Exception {
    Account first = accounts.newActivated(AccountRole.MEMBER);
    Account second = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(post("/api/skills/test-skill/activate").with(accounts.authenticatedAs(first)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/skills/activations").with(accounts.authenticatedAs(second)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
