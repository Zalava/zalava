package org.zalava.knowledge.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryProposalTest {

  private static final String NOW = "2026-09-16T10:00:00Z";
  private static final String ACTOR = "00000000-0000-0000-0000-000000000001";

  @Test
  void pendingProposalCarriesProvenanceAndProposedHistory() {
    MemoryProposal proposal =
        MemoryProposal.pending(
            "p-1",
            ACTOR,
            new MemoryProposalDraft(MemoryScope.PROJECT, "durable fact", Map.of("k", "v"), "run-1"),
            NOW);

    assertThat(proposal.status()).isEqualTo(MemoryProposal.Status.PENDING);
    assertThat(proposal.provenance()).isEqualTo(MemoryProvenance.of("proposal", "run-1"));
    assertThat(proposal.ownedBy(ACTOR)).isTrue();
    assertThat(proposal.history())
        .singleElement()
        .satisfies(event -> assertThat(event.type()).isEqualTo("proposed"));
    assertThat(proposal.resolvedAt()).isNull();
    assertThat(proposal.memoryId()).isNull();
  }

  @Test
  void approvalRecordsMemoryIdentityAndHistory() {
    MemoryProposal approved = pending().approved("memory-1", "2026-09-16T10:05:00Z");

    assertThat(approved.status()).isEqualTo(MemoryProposal.Status.APPROVED);
    assertThat(approved.memoryId()).isEqualTo("memory-1");
    assertThat(approved.resolvedAt()).isEqualTo("2026-09-16T10:05:00Z");
    assertThat(approved.history())
        .extracting(MemoryProposal.Event::type)
        .containsExactly("proposed", "approved");
  }

  @Test
  void resolutionTransitionsRequireAPendingProposal() {
    assertThatThrownBy(() -> pending().approved(null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> pending().approved("memory-1", NOW).approved("memory-2", NOW))
        .isInstanceOf(MemoryProposal.StaleProposalException.class);
    assertThatThrownBy(() -> pending().rejected("no", NOW).rejected("no", NOW))
        .isInstanceOf(MemoryProposal.StaleProposalException.class);
  }

  @Test
  void rejectionAndRevocationAreRecorded() {
    assertThat(pending().rejected("not durable", NOW))
        .satisfies(
            rejected -> {
              assertThat(rejected.status()).isEqualTo(MemoryProposal.Status.REJECTED);
              assertThat(rejected.history().get(1).detail()).isEqualTo("not durable");
            });
    assertThat(pending().revoked(NOW).status()).isEqualTo(MemoryProposal.Status.REVOKED);
    assertThat(pending().approved("memory-1", NOW).revoked(NOW).status())
        .isEqualTo(MemoryProposal.Status.REVOKED);
  }

  @Test
  void constructionRejectsIncompleteOrResolvedWithoutTimestamp() {
    assertThatThrownBy(
            () ->
                new MemoryProposal(
                    "p",
                    ACTOR,
                    MemoryScope.USER,
                    "text",
                    Map.of(),
                    MemoryProvenance.legacy(),
                    MemoryProposal.Status.APPROVED,
                    NOW,
                    null,
                    "memory-1",
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resolvedAt");
  }

  private static MemoryProposal pending() {
    return MemoryProposal.pending(
        "p-1", ACTOR, MemoryProposalDraft.of(MemoryScope.USER, "durable fact"), NOW);
  }
}
