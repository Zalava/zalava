package org.zalava.knowledge.skills.domain;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned, storage-independent metadata for one Zalava skill.
 *
 * <p>A descriptor is instruction metadata only: {@code recommendedTools} names Zalava tools the
 * skill may reference, but a descriptor never carries, resolves or executes a tool. Activation and
 * content loading are a separate concern (the maintained SkillsTool runtime), so discovery cannot
 * conflate metadata with executable capability.
 */
public record SkillDescriptor(
    String name,
    String version,
    String description,
    List<String> capabilities,
    List<String> recommendedTools,
    List<String> policyConstraints,
    List<String> validationSteps,
    String zalavaApiVersion,
    SkillVisibility visibility,
    SkillProvenance provenance,
    SkillStatus status) {

  public static final String DEFAULT_VERSION = "0.0.0";

  private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final Pattern SEMANTIC_VERSION =
      Pattern.compile(
          "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

  public SkillDescriptor {
    name = requireText(name, "name", 100);
    if (!KEBAB_CASE.matcher(name).matches()) {
      throw new InvalidSkillMetadataException("Skill name must be kebab-case: " + name);
    }
    version = requireText(version, "version", 64);
    description = requireText(description, "description", 2000);
    capabilities = immutable(capabilities, "capabilities");
    recommendedTools = immutable(recommendedTools, "recommendedTools");
    policyConstraints = immutable(policyConstraints, "policyConstraints");
    validationSteps = immutable(validationSteps, "validationSteps");
    zalavaApiVersion =
        zalavaApiVersion == null || zalavaApiVersion.isBlank() ? null : zalavaApiVersion.strip();
    Objects.requireNonNull(visibility, "visibility must not be null");
    Objects.requireNonNull(provenance, "provenance must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }

  /** Whether this descriptor's version is a concrete semantic version. */
  public boolean hasConcreteVersion() {
    return SEMANTIC_VERSION.matcher(version).matches();
  }

  public SkillDescriptor withStatus(SkillStatus newStatus) {
    return new SkillDescriptor(
        name,
        version,
        description,
        capabilities,
        recommendedTools,
        policyConstraints,
        validationSteps,
        zalavaApiVersion,
        visibility,
        provenance,
        newStatus);
  }

  private static List<String> immutable(List<String> values, String field) {
    List<String> safe = values == null ? List.of() : values;
    if (safe.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException(field + " must not contain blank values");
    }
    return safe.stream().map(String::strip).distinct().toList();
  }

  private static String requireText(String value, String field, int maximumLength) {
    if (value == null || value.isBlank()) {
      throw new InvalidSkillMetadataException(field + " must not be blank");
    }
    String normalized = value.strip();
    if (normalized.length() > maximumLength) {
      throw new InvalidSkillMetadataException(
          field + " must not exceed " + maximumLength + " characters");
    }
    return normalized;
  }

  public static final class InvalidSkillMetadataException extends IllegalArgumentException {
    public InvalidSkillMetadataException(String message) {
      super(message);
    }

    public InvalidSkillMetadataException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
