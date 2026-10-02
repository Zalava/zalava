package org.zalava.knowledge.skills.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.skills.domain.SkillActivation;

/** Actor-scoped, durable persistence for skill activations. */
public interface SkillActivationStore {

  SkillActivation save(SkillActivation activation);

  Optional<SkillActivation> find(Actor actor, String name);

  List<SkillActivation> list(Actor actor);
}
