package org.zalava.skills.application.port.in;

import java.util.List;
import java.util.Optional;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.skills.domain.SkillDescriptor;

/** Bounded, actor/policy-aware skill metadata discovery. */
public interface SkillQueries {

  List<SkillDescriptor> installed(AccountRole role);

  List<SkillDescriptor> active(AccountRole role);

  List<SkillDescriptor> remote(AccountRole role);

  List<SkillDescriptor> search(AccountRole role, String query, int limit);

  Optional<SkillDescriptor> find(AccountRole role, String name);

  /**
   * Resolves the highest-version descriptor for a name without applying actor visibility.
   *
   * <p>This is a SEA-owned authorization input for activation policy, which needs to distinguish
   * "hidden from this actor" from "unknown". It must never be exposed directly to an actor.
   */
  Optional<SkillDescriptor> describe(String name);
}
