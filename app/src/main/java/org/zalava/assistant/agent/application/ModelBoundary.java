package org.zalava.assistant.agent.application;

import java.util.Arrays;
import java.util.List;

/** Zalava-owned deterministic boundary for content crossing the model boundary. */
public final class ModelBoundary {

  public static final String REDACTION = "[REDACTED]";

  private final int maximumCharacters;
  private final List<String> secrets;
  private final ModelBoundaryAudit audit;

  public ModelBoundary(int maximumCharacters, String configuredSecrets) {
    this(maximumCharacters, configuredSecrets, event -> {});
  }

  public ModelBoundary(int maximumCharacters, String configuredSecrets, ModelBoundaryAudit audit) {
    if (maximumCharacters < 1)
      throw new IllegalArgumentException("model boundary maximum characters must be positive");
    this.maximumCharacters = maximumCharacters;
    this.secrets =
        Arrays.stream(configuredSecrets.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    this.audit = audit;
  }

  public String input(String content) {
    String redacted = redact(content);
    if (redacted.length() > maximumCharacters) {
      audit.record(
          new ModelBoundaryAuditEvent(
              ModelBoundaryAuditEvent.Decision.DENIED,
              content.length(),
              redacted.length(),
              secrets.size()));
      throw new ModelBoundaryViolation("Model input exceeds configured character limit");
    }
    audit.record(
        new ModelBoundaryAuditEvent(
            ModelBoundaryAuditEvent.Decision.ALLOWED,
            content.length(),
            redacted.length(),
            secrets.size()));
    return redacted;
  }

  public String redact(String content) {
    String value = content == null ? "" : content;
    for (String secret : secrets) value = value.replace(secret, REDACTION);
    return value;
  }

  /** Length of the longest configured secret, or {@code 0} when none are configured. */
  public int maximumSecretLength() {
    return secrets.stream().mapToInt(String::length).max().orElse(0);
  }

  /** Immutable view of the configured secrets, for streaming emission safety checks. */
  public List<String> secrets() {
    return secrets;
  }
}
