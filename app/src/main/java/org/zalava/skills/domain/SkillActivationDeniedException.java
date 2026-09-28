package org.zalava.skills.domain;

/** Raised when SEA policy forbids an actor from activating a discovered skill. */
public final class SkillActivationDeniedException extends RuntimeException {

  public SkillActivationDeniedException(String name) {
    super("SEA policy denies activating skill: " + name);
  }
}
