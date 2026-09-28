package org.zalava.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.memory.application.port.in.ActorMemoryQueries;
import org.zalava.memory.application.port.in.MemoryManagement;
import org.zalava.memory.application.port.in.MemoryPromotions;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryProposal;
import org.zalava.memory.domain.MemoryProposalDraft;
import org.zalava.memory.domain.MemoryScope;

class MemoryControllerTest {

  private final AccountLifecycle accounts = mock(AccountLifecycle.class);
  private final ActorMemoryQueries memories = mock(ActorMemoryQueries.class);
  private final MemoryPromotions promotions = mock(MemoryPromotions.class);
  private final MemoryManagement management = mock(MemoryManagement.class);
  private final Actor actor = new Actor(new AccountId(UUID.randomUUID()));
  private final UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(
          "memory-owner", "unused", List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
  private final DefaultCsrfToken csrf = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token");
  private MemoryController controller;

  @BeforeEach
  void setUp() {
    when(accounts.findByLoginName("memory-owner"))
        .thenReturn(
            Optional.of(
                new Account(
                    actor.accountId(),
                    "memory-owner",
                    "password-hash",
                    true,
                    AccountRole.MEMBER,
                    false,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    0)));
    controller =
        new MemoryController(
            memories, promotions, management, new AuthenticatedActorResolver(accounts));
  }

  @Test
  void browsesOrSearchesWithABoundedScopeFilter() {
    when(memories.recent(eq(actor), eq(MemoryScope.all()), eq(50))).thenReturn(List.of());
    when(memories.search(eq(actor), eq(Set.of(MemoryScope.PROJECT)), eq("provider"), eq(50)))
        .thenReturn(List.of());

    Model browse = new ExtendedModelMap();
    assertThat(controller.memory(null, null, authentication, browse, csrf)).isEqualTo("ui/memory");
    verify(memories).recent(actor, MemoryScope.all(), 50);

    Model search = new ExtendedModelMap();
    assertThat(controller.memory("provider", "PROJECT", authentication, search, csrf))
        .isEqualTo("ui/memory");
    verify(memories).search(actor, Set.of(MemoryScope.PROJECT), "provider", 50);
  }

  @Test
  void fallsBackToEveryScopeOnAnUnknownFilter() {
    when(memories.recent(eq(actor), eq(MemoryScope.all()), eq(50))).thenReturn(List.of());

    Model model = new ExtendedModelMap();
    controller.memory(null, "not-a-scope", authentication, model, csrf);

    MemoryController.MemoryModel rendered =
        (MemoryController.MemoryModel) model.getAttribute("model");
    assertThat(rendered.error()).isEqualTo("Unknown memory scope.");
    verify(memories).recent(actor, MemoryScope.all(), 50);
  }

  @Test
  void inspectsOnlyOwnedMemories() {
    Memory memory = memory("memory-1");
    when(memories.find(actor, "memory-1")).thenReturn(Optional.of(memory));
    when(memories.find(actor, "missing")).thenReturn(Optional.empty());

    Model model = new ExtendedModelMap();
    assertThat(controller.detail("memory-1", authentication, model, csrf))
        .isEqualTo("ui/memory-detail");
    assertThat(model.getAttribute("model")).isInstanceOf(MemoryController.MemoryDetailModel.class);

    assertThatThrownBy(
            () -> controller.detail("missing", authentication, new ExtendedModelMap(), csrf))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void editsOwnedMemoriesAndReRendersUnsafeEdits() {
    Memory memory = memory("memory-1");
    when(management.revise(actor, "memory-1", MemoryScope.AGENT, "after"))
        .thenReturn(Optional.of(memory));
    when(memories.find(actor, "memory-1")).thenReturn(Optional.of(memory));
    when(management.revise(actor, "memory-1", MemoryScope.USER, "the password is hunter2"))
        .thenThrow(
            new IllegalArgumentException(
                "A memory proposal must not contain credentials or secrets"));

    assertThat(
            controller.edit(
                "memory-1", "AGENT", "after", authentication, new ExtendedModelMap(), csrf))
        .isEqualTo("redirect:/memory/memory-1");

    Model model = new ExtendedModelMap();
    assertThat(
            controller.edit(
                "memory-1", "USER", "the password is hunter2", authentication, model, csrf))
        .isEqualTo("ui/memory-detail");
    MemoryController.MemoryDetailModel rendered =
        (MemoryController.MemoryDetailModel) model.getAttribute("model");
    assertThat(rendered.error()).contains("credentials");
    assertThat(rendered.text()).isEqualTo("the password is hunter2");
  }

  @Test
  void deletesConfirmedMemoriesAndRedactsUnconfirmedOnes() {
    var confirmed = new RedirectAttributesModelMap();
    controller.delete("memory-1", true, authentication, confirmed);
    verify(management).delete(actor, "memory-1");
    assertThat(confirmed.getFlashAttributes().get("memoryMessage")).isEqualTo("Memory deleted.");

    var unconfirmed = new RedirectAttributesModelMap();
    controller.delete("memory-1", false, authentication, unconfirmed);
    assertThat(unconfirmed.getFlashAttributes().get("memoryError"))
        .isEqualTo("Memory deletion could not be completed.");
  }

  @Test
  void proposesAndDecidesForTheAuthenticatedOwner() {
    when(promotions.propose(eq(actor), any(MemoryProposalDraft.class)))
        .thenReturn(proposal("proposal-1"));

    var propose = new RedirectAttributesModelMap();
    controller.propose("PROJECT", "durable fact", authentication, propose);
    verify(promotions).propose(actor, MemoryProposalDraft.of(MemoryScope.PROJECT, "durable fact"));
    assertThat(propose.getFlashAttributes().get("memoryMessage"))
        .isEqualTo("Memory proposed for review.");

    var approve = new RedirectAttributesModelMap();
    controller.approve("proposal-1", authentication, approve);
    verify(promotions).approve(actor, "proposal-1");
    assertThat(approve.getFlashAttributes().get("memoryMessage"))
        .isEqualTo("Memory approved and stored.");

    var reject = new RedirectAttributesModelMap();
    controller.reject("proposal-1", authentication, reject);
    verify(promotions).reject(actor, "proposal-1", null);
    assertThat(reject.getFlashAttributes().get("memoryMessage")).isEqualTo("Proposal rejected.");

    var revoke = new RedirectAttributesModelMap();
    controller.revoke("proposal-1", authentication, revoke);
    verify(promotions).revoke(actor, "proposal-1");
    assertThat(revoke.getFlashAttributes().get("memoryMessage")).isEqualTo("Proposal revoked.");
  }

  @Test
  void surfacesProposalPolicyFailuresWithoutInventingText() {
    when(promotions.propose(eq(actor), any(MemoryProposalDraft.class)))
        .thenThrow(
            new IllegalArgumentException(
                "A memory proposal must not contain credentials or secrets"));

    var redirect = new RedirectAttributesModelMap();
    controller.propose("USER", "the password is hunter2", authentication, redirect);

    assertThat(redirect.getFlashAttributes().get("memoryError"))
        .isEqualTo("A memory proposal must not contain credentials or secrets");
  }

  private Memory memory(String id) {
    return new Memory(id, MemoryScope.PROJECT, "fact", java.util.Map.of(), Instant.EPOCH);
  }

  private MemoryProposal proposal(String id) {
    return MemoryProposal.pending(
        id,
        actor.accountId().toString(),
        MemoryProposalDraft.of(MemoryScope.PROJECT, "fact"),
        "now");
  }
}
