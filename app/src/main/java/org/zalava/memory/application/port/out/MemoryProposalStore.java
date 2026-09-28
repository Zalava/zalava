package org.zalava.memory.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.domain.MemoryProposal;

/** Actor-scoped persistence for reviewable durable-memory proposals. */
public interface MemoryProposalStore {

  MemoryProposal save(MemoryProposal proposal);

  Optional<MemoryProposal> find(Actor actor, String proposalId);

  List<MemoryProposal> list(Actor actor);
}
