package org.zalava.knowledge.skills.domain;

/**
 * Raised when an activation request names a version that no longer matches the discovered skill.
 *
 * <p>The catalogue resolves the highest installed version, so a request pinned to an older or
 * unknown version is rejected instead of silently activating different content.
 */
public final class SkillStaleVersionException extends IllegalStateException {

  private final String expectedVersion;
  private final String currentVersion;

  public SkillStaleVersionException(String name, String expectedVersion, String currentVersion) {
    super(
        "Skill "
            + name
            + " is no longer at version "
            + expectedVersion
            + "; discovered version is "
            + currentVersion);
    this.expectedVersion = expectedVersion;
    this.currentVersion = currentVersion;
  }

  public String expectedVersion() {
    return expectedVersion;
  }

  public String currentVersion() {
    return currentVersion;
  }
}
