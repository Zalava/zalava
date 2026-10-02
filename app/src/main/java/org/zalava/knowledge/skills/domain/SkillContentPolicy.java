package org.zalava.knowledge.skills.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SEA-owned content-safety policy applied when an actor activates a skill body.
 *
 * <p>It is a deterministic boundary: a skill file or remote catalogue cannot bypass it, and it
 * enforces the context budget and the rule that skill instructions never override SEA authority.
 * The policy only inspects content; it never resolves or executes a recommended tool.
 */
public final class SkillContentPolicy {

  /** Default maximum characters of a skill body offered to the agent context. */
  public static final int MAXIMUM_CONTENT_LENGTH = 8_000;

  private static final String[] AUTHORITY_OVERRIDE_MARKERS = {
    "ignore previous instructions",
    "ignore all previous",
    "ignore the previous instructions",
    "disregard previous instructions",
    "disregard your instructions",
    "override the system",
    "override sea",
    "override your instructions",
    "bypass policy",
    "bypass the policy",
    "bypass security",
    "grant yourself",
    "you have no restrictions",
    "act as the system"
  };
  private static final String[] SECRET_MARKERS = {
    "password",
    "passphrase",
    "api key",
    "apikey",
    "client secret",
    "access token",
    "bearer ",
    "private key"
  };
  private static final Pattern CREDENTIAL_LIKE =
      Pattern.compile(
          "(?i)(sk-[a-z0-9]{16,}|ghp_[a-z0-9]{20,}|xox[baprs]-[a-z0-9-]{10,}|[a-f0-9]{40,})");

  private SkillContentPolicy() {}

  public static void validate(String name, String content) {
    validate(name, content, MAXIMUM_CONTENT_LENGTH);
  }

  /**
   * Validates an activating skill body.
   *
   * @throws SkillContentBudgetExceededException when the body exceeds {@code maximumCharacters}
   * @throws UnsafeSkillContentException when the body is blank or tries to claim authority or carry
   *     secrets
   */
  public static void validate(String name, String content, int maximumCharacters) {
    if (maximumCharacters < 1) {
      throw new IllegalArgumentException("A skill content budget must be positive");
    }
    if (content == null || content.isBlank()) {
      throw new UnsafeSkillContentException("A skill body must not be blank");
    }
    if (content.length() > maximumCharacters) {
      throw new SkillContentBudgetExceededException(name, content.length(), maximumCharacters);
    }
    String normalized = content.toLowerCase(Locale.ROOT);
    for (String marker : AUTHORITY_OVERRIDE_MARKERS) {
      if (normalized.contains(marker)) {
        throw new UnsafeSkillContentException(
            "Skill instructions must not attempt to override SEA authority");
      }
    }
    for (String marker : SECRET_MARKERS) {
      if (normalized.contains(marker)) {
        throw new UnsafeSkillContentException(
            "A skill body must not contain credentials or secrets");
      }
    }
    if (CREDENTIAL_LIKE.matcher(content).find()) {
      throw new UnsafeSkillContentException("A skill body must not contain credentials or secrets");
    }
  }

  /** Raised when a skill body exceeds the configured context content budget. */
  public static final class SkillContentBudgetExceededException extends IllegalArgumentException {
    private final String name;
    private final int contentCharacters;
    private final int maximumCharacters;

    public SkillContentBudgetExceededException(
        String name, int contentCharacters, int maximumCharacters) {
      super(
          "Skill "
              + name
              + " body must not exceed "
              + maximumCharacters
              + " characters (was "
              + contentCharacters
              + ")");
      this.name = name;
      this.contentCharacters = contentCharacters;
      this.maximumCharacters = maximumCharacters;
    }

    public String name() {
      return name;
    }

    public int contentCharacters() {
      return contentCharacters;
    }

    public int maximumCharacters() {
      return maximumCharacters;
    }
  }

  public static final class UnsafeSkillContentException extends IllegalArgumentException {
    public UnsafeSkillContentException(String message) {
      super(message);
    }
  }
}
