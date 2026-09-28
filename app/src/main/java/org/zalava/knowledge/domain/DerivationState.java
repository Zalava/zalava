package org.zalava.knowledge.domain;

/** A versioned processor result is promoted only after successful SEA validation. */
public enum DerivationState {
  CANDIDATE,
  ACTIVE,
  FAILED,
  REPLACED
}
