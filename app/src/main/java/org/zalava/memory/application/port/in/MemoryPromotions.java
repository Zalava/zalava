package org.zalava.memory.application.port.in;

import java.util.List;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.domain.MemoryProposal;
import org.zalava.memory.domain.MemoryProposalDraft;

/**
 * Reviewable durable-memory promotion. Only the owning actor may read or decide a proposal, and
 * only an explicit approval promotes content into durable memory.
 */
public interface MemoryPromotions {

  /** Records a pending proposal after SEA scope/content validation. */
  MemoryProposal propose(Actor actor, MemoryProposalDraft draft);

  List<MemoryProposal> pending(Actor actor);

  MemoryProposal get(Actor actor, String proposalId);

  /** Promotes an approved proposal into the actor's durable memory. */
  MemoryProposal approve(Actor actor, String proposalId);

  MemoryProposal reject(Actor actor, String proposalId, String reason);

  /** Cancels a pending proposal or deletes the memory produced by an approved one. */
  MemoryProposal revoke(Actor actor, String proposalId);
}
