package org.zalava.knowledge.skills.domain;

/** Raised when Zalava policy forbids an actor from activating a discovered skill. */
public final class SkillActivationDeniedException extends RuntimeException {

  public SkillActivationDeniedException(String name) {
    super("Zalava policy denies activating skill: " + name);
  }
}
