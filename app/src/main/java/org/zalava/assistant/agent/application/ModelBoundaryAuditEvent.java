package org.zalava.assistant.agent.application;

public record ModelBoundaryAuditEvent(
    Decision decision, int originalCharacters, int deliveredCharacters, int configuredSecretCount) {
  public ModelBoundaryAuditEvent {
    if (originalCharacters < 0 || deliveredCharacters < 0 || configuredSecretCount < 0) {
      throw new IllegalArgumentException("Invalid model boundary audit event");
    }
  }

  public enum Decision {
    ALLOWED,
    DENIED
  }
}
