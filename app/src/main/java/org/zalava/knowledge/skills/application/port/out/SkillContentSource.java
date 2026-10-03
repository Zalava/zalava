package org.zalava.knowledge.skills.application.port.out;

import java.util.Optional;

/** Loads the bounded body of one discovered skill for Zalava-owned activation. */
public interface SkillContentSource {

  Optional<String> load(String name);
}
