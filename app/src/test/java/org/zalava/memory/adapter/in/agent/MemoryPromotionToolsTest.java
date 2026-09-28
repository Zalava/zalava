package org.zalava.memory.adapter.in.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.adapter.out.filesystem.FileSystemMemoryProposalStore;
import org.zalava.memory.adapter.out.filesystem.FileSystemMemoryStore;
import org.zalava.memory.application.SeaMemoryPromotions;
import org.zalava.memory.domain.MemoryProposal;
import org.zalava.memory.domain.MemoryProposalDraft;
import org.zalava.memory.domain.MemoryScope;

class MemoryPromotionToolsTest {

  @TempDir Path workspace;

  private final ActorExecutionContext actors = new ActorExecutionContext();
  private final Actor actor = new Actor(AccountId.newId());
  private SeaMemoryPromotions promotions;
  private MemoryPromotionTools tools;

  @BeforeEach
  void setUp() {
    promotions =
        new SeaMemoryPromotions(
            new FileSystemMemoryProposalStore(workspace),
            new FileSystemMemoryStore(workspace),
            java.time.Instant::now);
    tools = new MemoryPromotionTools(promotions, actors);
  }

  @Test
  void requiresAnAuthenticatedOwner() {
    String result = tools.propose(MemoryProposalDraft.of(MemoryScope.USER, "durable"));

    assertThat(result).contains("REJECTED").contains("authenticated owner");
    assertThat(promotions.pending(actor)).isEmpty();
  }

  @Test
  void recordsAPendingProposalButNeverApprovesIt() {
    String result =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () -> tools.propose(MemoryProposalDraft.of(MemoryScope.PROJECT, "durable fact")));

    assertThat(result).contains("PENDING");
    assertThat(promotions.pending(actor))
        .singleElement()
        .satisfies(
            proposal -> assertThat(proposal.status()).isEqualTo(MemoryProposal.Status.PENDING));
  }

  @Test
  void rejectsTransientScopesAndSecretsWithoutPersisting() {
    String transientResult =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () -> tools.propose(MemoryProposalDraft.of(MemoryScope.EXECUTION, "run trace")));
    String secretResult =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () ->
                tools.propose(
                    new MemoryProposalDraft(
                        MemoryScope.USER, "api key is hunter2", Map.of(), null)));

    assertThat(transientResult).contains("REJECTED").contains("Transient");
    assertThat(secretResult).contains("REJECTED").contains("secrets");
    assertThat(promotions.pending(actor)).isEmpty();
  }

  @Test
  void rejectsADuplicateOfAnAlreadyPromotedMemory() {
    MemoryProposal proposal =
        promotions.propose(actor, MemoryProposalDraft.of(MemoryScope.USER, "durable fact"));
    promotions.approve(actor, proposal.id());

    String result =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () -> tools.propose(MemoryProposalDraft.of(MemoryScope.USER, "durable fact")));

    assertThat(result).contains("REJECTED").contains("identical durable memory");
  }
}
