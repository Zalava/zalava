package org.zalava.knowledge.domain;

/** The current actor has no authority to mutate this source. */
public final class KnowledgeOwnershipDenied extends RuntimeException {
  public KnowledgeOwnershipDenied(String message) {
    super(message);
  }
}
