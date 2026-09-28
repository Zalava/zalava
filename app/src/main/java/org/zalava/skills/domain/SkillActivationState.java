package org.zalava.skills.domain;

/** SEA-owned lifecycle state of one actor's skill activation. */
public enum SkillActivationState {
  /** The actor selected this skill; its content may be offered to that actor's agent context. */
  ACTIVE,
  /** The actor disabled the skill; its content is never offered again until reactivated. */
  DEACTIVATED
}
