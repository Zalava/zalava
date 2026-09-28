package org.zalava.agent.application;

import java.util.List;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeEvidenceQueries;
import org.zalava.knowledge.domain.KnowledgeEvidence;

/**
 * Reads compact knowledge evidence only for the authenticated actor currently bound to the agent
 * turn. It deliberately owns no actor input and cannot widen the visibility decision made by {@link
 * KnowledgeEvidenceQueries}.
 */
public final class KnowledgeContextEnrichment {
  private static final int MAX_RESULTS = 3;
  private final KnowledgeEvidenceQueries evidence;
  private final ActorExecutionContext actors;
  private final ModelBoundary boundary;
  private final boolean enabled;

  public KnowledgeContextEnrichment(
      KnowledgeEvidenceQueries evidence, ActorExecutionContext actors, ModelBoundary boundary) {
    this(evidence, actors, boundary, true);
  }

  public KnowledgeContextEnrichment(
      KnowledgeEvidenceQueries evidence,
      ActorExecutionContext actors,
      ModelBoundary boundary,
      boolean enabled) {
    this.evidence = evidence;
    this.actors = actors;
    this.boundary = boundary;
    this.enabled = enabled;
  }

  public List<KnowledgeEvidence> select(String input) {
    if (!enabled || input == null || input.isBlank()) {
      return List.of();
    }
    return actors
        .currentPrincipal()
        .map(principal -> search(principal.actor(), input))
        .orElseGet(List::of);
  }

  private List<KnowledgeEvidence> search(Actor actor, String input) {
    try {
      return evidence.search(actor, input, MAX_RESULTS);
    } catch (IllegalArgumentException invalid) {
      return List.of();
    }
  }

  public String render(List<KnowledgeEvidence> selected) {
    return selected.stream()
        .map(
            value ->
                "- source=%s; derivation=%s; citation=/knowledge/%s; excerpt=%s"
                    .formatted(
                        boundary.redact(value.displayName()),
                        value.derivationVersion(),
                        value.sourceId(),
                        boundary.input(value.excerpt())))
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElse("");
  }
}
