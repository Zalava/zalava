package org.zalava.memory.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.application.port.in.MemoryPromotions;
import org.zalava.memory.application.port.out.ActorMemoryStore;
import org.zalava.memory.domain.MemoryProposal;
import org.zalava.memory.domain.MemoryProposalDraft;
import org.zalava.memory.domain.MemoryScope;
import org.zalava.support.AuthenticatedSeaComponentTest;
import org.zalava.support.ComponentTestAccounts;

/**
 * Full-context MockMvc component test for the actor-owned memory-proposal review surface. It drives
 * the real controller, actor resolver, proposal store and actor memory store, so ownership,
 * approval-to-memory, rejection, revocation and stale-decision handling are proven over real HTTP.
 */
@AuthenticatedSeaComponentTest
class MemoryProposalControllerComponentTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MemoryPromotions promotions;
  @Autowired private ActorMemoryStore memories;
  @Autowired private ComponentTestAccounts accounts;

  @Test
  void ownerSeesAndApprovesThePendingProposalIntoDurableMemory() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    MemoryProposal proposal =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.PROJECT, "durable fact"));

    mockMvc
        .perform(get("/api/memory-proposals").with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].proposalId").value(proposal.id()))
        .andExpect(jsonPath("$[0].status").value("PENDING"));

    mockMvc
        .perform(
            post("/api/memory-proposals/" + proposal.id() + "/approve")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.history[1].type").value("approved"));

    String memoryId = promotions.get(actor, proposal.id()).memoryId();
    assertThat(memoryId).isNotBlank();
    assertThat(memories.find(actor, memoryId)).isPresent();
    assertThat(promotions.pending(actor)).isEmpty();
  }

  @Test
  void anotherActorCannotReadOrDecideTheProposal() throws Exception {
    Account owner = accounts.newActivated(AccountRole.MEMBER);
    Account other = accounts.newActivated(AccountRole.MEMBER);
    Actor ownerActor = new Actor(owner.id());
    MemoryProposal proposal =
        promotions.propose(ownerActor, MemoryProposalDraft.of(MemoryScope.USER, "private fact"));

    mockMvc
        .perform(
            get("/api/memory-proposals/" + proposal.id()).with(accounts.authenticatedAs(other)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/memory-proposals/" + proposal.id() + "/approve")
                .with(accounts.authenticatedAs(other)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/memory-proposals/" + proposal.id() + "/reject")
                .with(accounts.authenticatedAs(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"no\"}"))
        .andExpect(status().isNotFound());

    assertThat(promotions.get(ownerActor, proposal.id()).status())
        .isEqualTo(MemoryProposal.Status.PENDING);
  }

  @Test
  void rejectionAndRevocationAreReportedAndRevocationDeletesTheMemory() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    MemoryProposal rejectedProposal =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.USER, "reject me"));

    mockMvc
        .perform(
            post("/api/memory-proposals/" + rejectedProposal.id() + "/reject")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"not durable\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.history[1].detail").value("not durable"));

    MemoryProposal promoted =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.AGENT, "revoke me"));
    String memoryId = promotions.approve(actor, promoted.id()).memoryId();

    mockMvc
        .perform(
            post("/api/memory-proposals/" + promoted.id() + "/revoke")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVOKED"));

    assertThat(memories.find(actor, memoryId)).isEmpty();
  }

  @Test
  void aStaleDecisionIsAConflict() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    MemoryProposal proposal =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.PROJECT, "decide once"));
    promotions.approve(actor, proposal.id());

    mockMvc
        .perform(
            post("/api/memory-proposals/" + proposal.id() + "/approve")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().isConflict());
  }
}
