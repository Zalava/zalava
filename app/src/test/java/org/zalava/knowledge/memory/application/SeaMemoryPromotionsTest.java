package org.zalava.knowledge.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.memory.adapter.out.filesystem.FileSystemMemoryProposalStore;
import org.zalava.knowledge.memory.adapter.out.filesystem.FileSystemMemoryStore;
import org.zalava.knowledge.memory.domain.MemoryContentPolicy;
import org.zalava.knowledge.memory.domain.MemoryProposal;
import org.zalava.knowledge.memory.domain.MemoryProposalDraft;
import org.zalava.knowledge.memory.domain.MemoryScope;

class SeaMemoryPromotionsTest {

  @TempDir Path workspace;

  private final Actor actor = new Actor(AccountId.newId());
  private final Actor other = new Actor(AccountId.newId());
  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-09-16T10:00:00Z"));

  private FileSystemMemoryStore memoryStore() {
    return new FileSystemMemoryStore(workspace);
  }

  private SeaMemoryPromotions promotions(FileSystemMemoryStore memories) {
    return new SeaMemoryPromotions(
        new FileSystemMemoryProposalStore(workspace), memories, now::get);
  }

  @Test
  void proposeCreatesPendingProposalAndIsIdempotentForIdenticalText() {
    SeaMemoryPromotions promotions = promotions(memoryStore());
    MemoryProposal first =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.PROJECT, "durable fact"));
    MemoryProposal second =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.PROJECT, "durable fact"));

    assertThat(first.status()).isEqualTo(MemoryProposal.Status.PENDING);
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(promotions.pending(actor)).hasSize(1);
  }

  @Test
  void proposeRejectsTransientAndSensitiveContent() {
    SeaMemoryPromotions promotions = promotions(memoryStore());

    assertThatThrownBy(
            () -> promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.EXECUTION, "trace")))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);
    assertThatThrownBy(
            () ->
                promotions.propose(
                    actor, MemoryProposalDraft.of(MemoryScope.USER, "the password is hunter2")))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);
    assertThat(promotions.pending(actor)).isEmpty();
  }

  @Test
  void approvePromotesIntoActorMemoryWithProposalProvenance() {
    FileSystemMemoryStore memories = memoryStore();
    SeaMemoryPromotions promotions = promotions(memories);
    MemoryProposal proposal =
        promotions.propose(
            actor,
            new MemoryProposalDraft(
                MemoryScope.PROJECT, "durable fact", Map.of("k", "v"), "run-1"));

    now.set(Instant.parse("2026-09-16T10:05:00Z"));
    MemoryProposal approved = promotions.approve(actor, proposal.id());

    assertThat(approved.status()).isEqualTo(MemoryProposal.Status.APPROVED);
    assertThat(approved.memoryId()).isNotNull();
    assertThat(memories.find(actor, approved.memoryId()))
        .get()
        .satisfies(
            memory -> {
              assertThat(memory.scope()).isEqualTo(MemoryScope.PROJECT);
              assertThat(memory.text()).isEqualTo("durable fact");
              assertThat(memory.provenance().source()).isEqualTo("proposal");
              assertThat(memory.provenance().reference()).isEqualTo("run-1");
            });
  }

  @Test
  void proposeRejectsAnExistingDurableDuplicate() {
    FileSystemMemoryStore memories = memoryStore();
    SeaMemoryPromotions promotions = promotions(memories);
    MemoryProposal proposal =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.USER, "durable fact"));
    promotions.approve(actor, proposal.id());

    assertThatThrownBy(
            () ->
                promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.USER, "durable fact")))
        .isInstanceOf(SeaMemoryPromotions.DuplicateMemoryException.class);
  }

  @Test
  void rejectRecordsTheReason() {
    SeaMemoryPromotions promotions = promotions(memoryStore());
    MemoryProposal proposal =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.USER, "maybe"));

    MemoryProposal rejected = promotions.reject(actor, proposal.id(), "not durable");

    assertThat(rejected.status()).isEqualTo(MemoryProposal.Status.REJECTED);
    assertThat(rejected.history()).extracting(MemoryProposal.Event::detail).contains("not durable");
  }

  @Test
  void revokeCancelsPendingAndDeletesTheApprovedMemory() {
    FileSystemMemoryStore memories = memoryStore();
    SeaMemoryPromotions promotions = promotions(memories);
    MemoryProposal pending =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.AGENT, "lesson one"));
    assertThat(promotions.revoke(actor, pending.id()).status())
        .isEqualTo(MemoryProposal.Status.REVOKED);

    MemoryProposal promoted =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.AGENT, "lesson two"));
    MemoryProposal approved = promotions.approve(actor, promoted.id());
    assertThat(memories.find(actor, approved.memoryId())).isPresent();

    MemoryProposal revoked = promotions.revoke(actor, promoted.id());
    assertThat(revoked.status()).isEqualTo(MemoryProposal.Status.REVOKED);
    assertThat(memories.find(actor, approved.memoryId())).isEmpty();
  }

  @Test
  void anotherActorCannotReadOrDecideTheProposal() {
    SeaMemoryPromotions promotions = promotions(memoryStore());
    MemoryProposal proposal =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.USER, "private"));

    assertThatThrownBy(() -> promotions.get(other, proposal.id()))
        .isInstanceOf(SeaMemoryPromotions.NotFoundException.class);
    assertThatThrownBy(() -> promotions.approve(other, proposal.id()))
        .isInstanceOf(SeaMemoryPromotions.NotFoundException.class);
    assertThatThrownBy(() -> promotions.reject(other, proposal.id(), "no"))
        .isInstanceOf(SeaMemoryPromotions.NotFoundException.class);
    assertThat(promotions.pending(other)).isEmpty();
  }

  @Test
  void decisionsRequireAPendingProposal() {
    SeaMemoryPromotions promotions = promotions(memoryStore());
    MemoryProposal proposal =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.USER, "once"));
    promotions.approve(actor, proposal.id());

    assertThatThrownBy(() -> promotions.approve(actor, proposal.id()))
        .isInstanceOf(MemoryProposal.StaleProposalException.class);
    assertThatThrownBy(() -> promotions.reject(actor, proposal.id(), "no"))
        .isInstanceOf(MemoryProposal.StaleProposalException.class);
  }

  @Test
  void pendingProposalsAndDecisionsSurviveARestart() {
    FileSystemMemoryStore memories = memoryStore();
    MemoryProposal proposal =
        promotions(memories).propose(actor, MemoryProposalDraft.of(MemoryScope.PROJECT, "restart"));

    SeaMemoryPromotions restarted = promotions(memories);

    assertThat(restarted.pending(actor))
        .extracting(MemoryProposal::id)
        .containsExactly(proposal.id());
    assertThat(restarted.approve(actor, proposal.id()).status())
        .isEqualTo(MemoryProposal.Status.APPROVED);
  }
}
