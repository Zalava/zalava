package org.zalava.skills.application.port.in;

import java.util.List;
import java.util.Optional;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.skills.application.SkillActivationMetrics;
import org.zalava.skills.domain.SkillActivation;

/**
 * Actor-owned, policy-controlled skill activation.
 *
 * <p>Activation validates a discovered skill against SEA policy and the configured content budget,
 * persists the selection for the requesting actor and makes its body available only to that actor's
 * context as untrusted instructions. It never grants a tool or permission, and deactivation is the
 * rollback path.
 */
public interface SkillActivations {

  SkillActivation activate(Actor actor, AccountRole role, String name, String expectedVersion);

  SkillActivation deactivate(Actor actor, String name);

  List<SkillActivation> active(Actor actor);

  Optional<SkillActivation> find(Actor actor, String name);

  SkillActivationMetrics metrics();
}
