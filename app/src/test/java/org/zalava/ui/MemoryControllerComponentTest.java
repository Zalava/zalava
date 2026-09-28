package org.zalava.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.application.port.in.MemoryPromotions;
import org.zalava.memory.application.port.out.ActorMemoryStore;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryProposal;
import org.zalava.memory.domain.MemoryProposalDraft;
import org.zalava.memory.domain.MemoryProvenance;
import org.zalava.memory.domain.MemoryScope;
import org.zalava.support.AuthenticatedSeaComponentTest;
import org.zalava.support.ComponentTestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context MockMvc component test for the product Memory screen. It drives the real controller,
 * filter chain, actor resolver, memory store and promotion service, so browse/search, inspect,
 * edit, delete, proposal review, isolation and validation are proven over real HTTP.
 */
@AuthenticatedSeaComponentTest
class MemoryControllerComponentTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ActorMemoryStore memories;
  @Autowired private MemoryPromotions promotions;
  @Autowired private ComponentTestAccounts accounts;

  @Test
  void rendersTheOwnersMemoryWithScopeAndProvenance() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    memories.remember(
        actor,
        new MemoryDraft(
            MemoryScope.PROJECT,
            "SEA modules expose providers",
            Map.of(),
            MemoryProvenance.of("user", "run-7")));

    mockMvc
        .perform(get("/memory").with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("SEA modules expose providers")))
        .andExpect(content().string(containsString("PROJECT")))
        .andExpect(content().string(containsString("user")))
        .andExpect(content().string(containsString("run-7")));
  }

  @Test
  void proposesApprovesAndRevokesThroughTheProductSurface() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());

    mockMvc
        .perform(
            post("/memory/proposals")
                .param("scope", "PROJECT")
                .param("text", "durable product fact")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("memoryMessage", "Memory proposed for review."));

    mockMvc
        .perform(get("/memory").with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("durable product fact")))
        .andExpect(content().string(containsString("data-memory-proposal")));

    MemoryProposal proposal = promotions.pending(actor).get(0);
    mockMvc
        .perform(
            post("/memory/proposals/" + proposal.id() + "/approve")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("memoryMessage", "Memory approved and stored."));

    String memoryId = promotions.get(actor, proposal.id()).memoryId();
    mockMvc
        .perform(get("/memory/" + memoryId).with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("durable product fact")))
        .andExpect(content().string(containsString("proposal")));

    mockMvc
        .perform(
            post("/memory/proposals/" + proposal.id() + "/revoke")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("memoryMessage", "Proposal revoked."));

    org.assertj.core.api.Assertions.assertThat(memories.find(actor, memoryId)).isEmpty();
  }

  @Test
  void editsAndDeletesThroughTheProductSurface() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    Memory memory = memories.remember(actor, new MemoryDraft(MemoryScope.USER, "before", Map.of()));

    mockMvc
        .perform(
            post("/memory/" + memory.id() + "/edit")
                .param("scope", "AGENT")
                .param("text", "after")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/memory/" + memory.id()).with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("after")))
        .andExpect(content().string(containsString("Last revised")));

    Memory reloaded = memories.find(actor, memory.id()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(reloaded.text()).isEqualTo("after");
    org.assertj.core.api.Assertions.assertThat(reloaded.scope()).isEqualTo(MemoryScope.AGENT);
    org.assertj.core.api.Assertions.assertThat(reloaded.updatedAt()).isNotNull();

    mockMvc
        .perform(
            post("/memory/" + memory.id() + "/delete")
                .param("confirmed", "true")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("memoryMessage", "Memory deleted."));

    mockMvc
        .perform(get("/memory").with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("after"))));
  }

  @Test
  void rejectsSecretContentWithoutWritingMemory() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());

    mockMvc
        .perform(
            post("/memory/proposals")
                .param("scope", "USER")
                .param("text", "the password is hunter2")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            flash()
                .attribute(
                    "memoryError", "A memory proposal must not contain credentials or secrets"));

    org.assertj.core.api.Assertions.assertThat(memories.recent(actor, 10)).isEmpty();
    org.assertj.core.api.Assertions.assertThat(promotions.pending(actor)).isEmpty();
  }

  @Test
  void isolatesMemoriesBetweenActors() throws Exception {
    Account owner = accounts.newActivated(AccountRole.MEMBER);
    Account other = accounts.newActivated(AccountRole.MEMBER);
    Memory memory =
        memories.remember(
            new Actor(owner.id()),
            new MemoryDraft(MemoryScope.USER, "owner secret fact", Map.of()));

    mockMvc
        .perform(get("/memory").with(accounts.authenticatedAs(other)))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("owner secret fact"))));

    mockMvc
        .perform(get("/memory/" + memory.id()).with(accounts.authenticatedAs(other)))
        .andExpect(status().isNotFound());
  }

  @Test
  void requiresAuthenticationAndAllowsMembers() throws Exception {
    Account member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc.perform(get("/memory").with(anonymous())).andExpect(status().is3xxRedirection());
    mockMvc
        .perform(get("/memory").with(accounts.authenticatedAs(member)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/memory/unknown-id").with(accounts.authenticatedAs(member)))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsUnconfirmedDeletion() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    Memory memory =
        memories.remember(actor, new MemoryDraft(MemoryScope.USER, "keep me", Map.of()));

    mockMvc
        .perform(
            post("/memory/" + memory.id() + "/delete")
                .param("confirmed", "false")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("memoryError", "Memory deletion could not be completed."));

    org.assertj.core.api.Assertions.assertThat(memories.find(actor, memory.id())).isPresent();
  }

  @Test
  void refusesForeignProposalDecisions() throws Exception {
    Account owner = accounts.newActivated(AccountRole.MEMBER);
    Account other = accounts.newActivated(AccountRole.MEMBER);
    MemoryProposal proposal =
        promotions.propose(
            new Actor(owner.id()), MemoryProposalDraft.of(MemoryScope.USER, "private proposal"));

    mockMvc
        .perform(
            post("/memory/proposals/" + proposal.id() + "/approve")
                .with(accounts.authenticatedAs(other)))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attributeExists("memoryError"));

    org.assertj.core.api.Assertions.assertThat(
            promotions.get(new Actor(owner.id()), proposal.id()).status())
        .isEqualTo(MemoryProposal.Status.PENDING);
  }
}
