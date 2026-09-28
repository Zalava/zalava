package org.zalava.skills.application;

/**
 * Bounded, deterministic activation evidence.
 *
 * <p>Counters are monotonically increasing for the life of the process and are surfaced so an
 * acceptance lane can record {@code SKILL-METRICS} evidence without exposing skill content.
 */
public record SkillActivationMetrics(
    int activations,
    int refreshes,
    int reactivations,
    int deactivations,
    int denials,
    int staleVersions,
    int budgetRejections,
    int contentCharacters) {

  public static final SkillActivationMetrics EMPTY =
      new SkillActivationMetrics(0, 0, 0, 0, 0, 0, 0, 0);
}
