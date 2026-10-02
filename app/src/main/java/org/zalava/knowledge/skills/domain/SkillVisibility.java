package org.zalava.knowledge.skills.domain;

/** Who may discover a skill's metadata. */
public enum SkillVisibility {
  /** Visible to every authenticated actor. */
  ALL,
  /** Visible only to administrators. */
  ADMIN;

  public static SkillVisibility parse(String value) {
    if (value == null || value.isBlank()) {
      return ALL;
    }
    return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
      case "all" -> ALL;
      case "admin" -> ADMIN;
      default ->
          throw new SkillDescriptor.InvalidSkillMetadataException(
              "Skill visibility must be 'all' or 'admin': " + value);
    };
  }
}
