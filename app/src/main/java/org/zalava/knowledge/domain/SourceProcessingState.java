package org.zalava.knowledge.domain;

/** Host-owned reprocessing state; processor modules never mutate authoritative source state. */
public enum SourceProcessingState {
  PENDING,
  PROCESSING,
  READY,
  FAILED,
  CANCELLED,
  DELETION_REQUESTED,
  DELETED
}
