package org.zalava.agent.application;

import java.util.List;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.Actor;
import org.zalava.skills.application.port.in.SkillActivations;
import org.zalava.skills.domain.SkillActivation;

/**
 * Reads the authenticated actor's explicitly activated skills for the current agent turn.
 *
 * <p>It owns no actor input and cannot widen the activation decision made by {@link
 * SkillActivations}: it merely renders already-validated bodies under an untrusted heading. A skill
 * body can never add a tool, grant a permission or override SEA authority.
 */
public final class SkillContextEnrichment {

  private static final int MAX_RESULTS = 3;

  private final SkillActivations activations;
  private final ActorExecutionContext actors;
  private final ModelBoundary boundary;
  private final boolean enabled;

  public SkillContextEnrichment(
      SkillActivations activations, ActorExecutionContext actors, ModelBoundary boundary) {
    this(activations, actors, boundary, true);
  }

  public SkillContextEnrichment(
      SkillActivations activations,
      ActorExecutionContext actors,
      ModelBoundary boundary,
      boolean enabled) {
    this.activations = activations;
    this.actors = actors;
    this.boundary = boundary;
    this.enabled = enabled;
  }

  public List<SkillActivation> select() {
    if (!enabled) {
      return List.of();
    }
    return actors
        .currentPrincipal()
        .map(principal -> active(principal.actor()))
        .orElseGet(List::of);
  }

  private List<SkillActivation> active(Actor actor) {
    try {
      return activations.active(actor).stream().limit(MAX_RESULTS).toList();
    } catch (RuntimeException invalid) {
      return List.of();
    }
  }

  public String render(List<SkillActivation> selected) {
    return selected.stream()
        .map(
            activation ->
                "- skill=%s@%s: %s"
                    .formatted(
                        activation.name(),
                        activation.version(),
                        boundary.input(activation.content())))
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElse("");
  }
}
