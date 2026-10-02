package org.zalava.knowledge.skills.domain;

/** Lifecycle state of a discovered skill. */
public enum SkillStatus {
  /** Present in a configured local skills directory. */
  INSTALLED,
  /** Installed and enabled for the current configuration. */
  ACTIVE,
  /** Known from a remote catalogue but not installed locally. */
  REMOTE
}
