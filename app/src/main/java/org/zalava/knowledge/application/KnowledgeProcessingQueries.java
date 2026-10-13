package org.zalava.knowledge.application;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.zalava.api.extensions.content.ContentExtractionFailureCategory;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/** Actor-scoped, redacted processing diagnostics and currently loaded extraction capability. */
public final class KnowledgeProcessingQueries {
  private final KnowledgeSourceStore sources;
  private final KnowledgeDerivationStore derivations;
  private final KnowledgeExtractionRecordStore records;
  private final RuntimeQueries runtime;

  public KnowledgeProcessingQueries(
      KnowledgeSourceStore sources,
      KnowledgeDerivationStore derivations,
      KnowledgeExtractionRecordStore records,
      RuntimeQueries runtime) {
    this.sources = sources;
    this.derivations = derivations;
    this.records = records;
    this.runtime = runtime;
  }

  public boolean extractorAvailable() {
    return runtime.findService(ContentExtractor.CONTRACT).isPresent();
  }

  public Optional<Status> inspect(Actor actor, KnowledgeSourceId id) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(id, "id");
    return sources.visibleTo(actor).stream()
        .filter(source -> source.id().equals(id))
        .findFirst()
        .map(
            source ->
                new Status(
                    source.processingState(),
                    extractorAvailable(),
                    derivations.findBySourceId(id).stream()
                        .max(Comparator.comparingLong(KnowledgeDerivation::version))
                        .flatMap(derivation -> records.find(id, derivation.version()))
                        .map(KnowledgeExtractionRecord::failureCategory)
                        .orElse(null)));
  }

  public record Status(
      SourceProcessingState state,
      boolean extractorAvailable,
      ContentExtractionFailureCategory failureCategory) {}
}
