package org.zalava.knowledge.memory.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.memory.domain.MemoryProposal;
import org.zalava.knowledge.memory.domain.MemoryProposalDraft;
import org.zalava.knowledge.memory.domain.MemoryProvenance;
import org.zalava.knowledge.memory.domain.MemoryScope;

class FileSystemMemoryProposalStoreTest {

  @TempDir Path workspace;

  private final Actor actor = new Actor(AccountId.newId());
  private final Actor other = new Actor(AccountId.newId());

  @Test
  void persistsAndReloadsProposalsAcrossStoreInstances() {
    var store = new FileSystemMemoryProposalStore(workspace);
    MemoryProposal proposal = proposal("2026-09-16T10:00:00Z");
    store.save(proposal);

    var reloaded = new FileSystemMemoryProposalStore(workspace);

    assertThat(reloaded.find(actor, proposal.id())).contains(proposal);
    assertThat(reloaded.list(actor)).extracting(MemoryProposal::id).containsExactly(proposal.id());
  }

  @Test
  void proposalsAreInvisibleToAnotherActor() {
    var store = new FileSystemMemoryProposalStore(workspace);
    MemoryProposal proposal = proposal("2026-09-16T10:00:00Z");
    store.save(proposal);

    assertThat(store.find(other, proposal.id())).isEmpty();
    assertThat(store.list(other)).isEmpty();
  }

  @Test
  void listReturnsNewestFirstAndSkipsCorruptRecords() throws Exception {
    var store = new FileSystemMemoryProposalStore(workspace);
    MemoryProposal older = proposal("2026-09-16T10:00:00Z");
    store.save(older);
    MemoryProposal newer = proposal("2026-09-16T11:00:00Z");
    store.save(newer);
    Path corrupt =
        workspace
            .resolve("users")
            .resolve(actor.accountId().toString())
            .resolve("memory-proposals")
            .resolve("corrupt.json");
    Files.writeString(corrupt, "{");

    assertThat(store.list(actor))
        .extracting(MemoryProposal::id)
        .containsExactly(newer.id(), older.id());
  }

  @Test
  void invalidProposalIdsAndOwnersAreRejected() {
    var store = new FileSystemMemoryProposalStore(workspace);

    assertThatThrownBy(() -> store.find(actor, "not-a-uuid"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                store.save(
                    new MemoryProposal(
                        UUID.randomUUID().toString(),
                        "not-a-uuid",
                        MemoryScope.USER,
                        "text",
                        Map.of(),
                        MemoryProvenance.legacy(),
                        MemoryProposal.Status.PENDING,
                        "2026-09-16T10:00:00Z",
                        null,
                        null,
                        List.of(new MemoryProposal.Event("proposed", "2026-09-16T10:00:00Z", "")))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private MemoryProposal proposal(String createdAt) {
    return MemoryProposal.pending(
        UUID.randomUUID().toString(),
        actor.accountId().toString(),
        MemoryProposalDraft.of(MemoryScope.PROJECT, "durable fact"),
        createdAt);
  }
}
