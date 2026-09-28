package org.zalava.knowledge.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeSearchStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;

/** Authenticated library reads; native search candidates never decide authorization. */
public final class KnowledgeLibrary {
  private static final int MAX_QUERY_CHARS = 256;
  private static final int DEFAULT_RESULTS = 20;
  private static final int MAX_RESULTS = 50;

  private final KnowledgeSourceStore sources;
  private final KnowledgeSearchStore search;

  public KnowledgeLibrary(KnowledgeSourceStore sources, KnowledgeSearchStore search) {
    this.sources = Objects.requireNonNull(sources, "sources");
    this.search = Objects.requireNonNull(search, "search");
  }

  public List<SourceSummary> browse(Actor actor) {
    return browse(actor, MetadataFilter.none(), DEFAULT_RESULTS);
  }

  public List<SourceSummary> browse(Actor actor, MetadataFilter filter, int limit) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(filter, "filter");
    validateLimit(limit);
    return sources.visibleTo(actor).stream()
        .filter(filter::matches)
        .map(SourceSummary::from)
        .sorted(order())
        .limit(limit)
        .toList();
  }

  /** Returns metadata only when the source is currently visible to the authenticated actor. */
  public Optional<SourceDetail> inspect(Actor actor, KnowledgeSourceId id) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(id, "id");
    return sources.visibleTo(actor).stream()
        .filter(source -> source.id().equals(id))
        .findFirst()
        .map(SourceDetail::from);
  }

  public List<SourceSummary> search(Actor actor, String query, int limit) {
    return search(actor, query, MetadataFilter.none(), limit);
  }

  public List<SourceSummary> search(Actor actor, String query, MetadataFilter filter, int limit) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(filter, "filter");
    String normalized = normalizedQuery(query);
    validateLimit(limit);
    Set<KnowledgeSourceId> visible =
        sources.visibleTo(actor).stream()
            .filter(filter::matches)
            .map(KnowledgeSource::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return search
        .findCandidates(
            new KnowledgeSearchStore.SearchCriteria(
                normalized, filter.contentType(), filter.processingState(), limit, actor))
        .stream()
        .map(KnowledgeSearchStore.Candidate::sourceId)
        .filter(visible::contains)
        .flatMap(id -> sources.findById(id).stream())
        .filter(filter::matches)
        .map(SourceSummary::from)
        .sorted(order())
        .limit(limit)
        .toList();
  }

  private static Comparator<SourceSummary> order() {
    return Comparator.comparing(SourceSummary::updatedAt)
        .reversed()
        .thenComparing(value -> value.id().value());
  }

  private static String normalizedQuery(String query) {
    if (query == null || query.isBlank() || query.length() > MAX_QUERY_CHARS) {
      throw new IllegalArgumentException("Knowledge query is invalid");
    }
    return query.strip();
  }

  private static void validateLimit(int limit) {
    if (limit < 1 || limit > MAX_RESULTS) {
      throw new IllegalArgumentException("Knowledge search limit is out of bounds");
    }
  }

  public record MetadataFilter(String contentType, SourceProcessingState processingState) {
    public MetadataFilter {
      if (contentType != null) {
        contentType = contentType.strip();
        if (!contentType.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
            || contentType.length() > 255) {
          throw new IllegalArgumentException("Knowledge content type filter is invalid");
        }
      }
    }

    public static MetadataFilter none() {
      return new MetadataFilter(null, null);
    }

    private boolean matches(KnowledgeSource source) {
      return (contentType == null || contentType.equals(source.contentType()))
          && (processingState == null || processingState == source.processingState());
    }
  }

  public record SourceSummary(
      KnowledgeSourceId id,
      String displayName,
      String contentType,
      long byteCount,
      KnowledgeVisibility visibility,
      SourceProcessingState processingState,
      Instant updatedAt) {
    private static SourceSummary from(KnowledgeSource source) {
      return new SourceSummary(
          source.id(),
          source.displayName(),
          source.contentType(),
          source.byteCount(),
          source.visibility(),
          source.processingState(),
          source.updatedAt());
    }
  }

  /** Redacted source provenance suitable for authenticated product rendering. */
  public record SourceDetail(
      KnowledgeSourceId id,
      String displayName,
      String contentType,
      long byteCount,
      KnowledgeVisibility visibility,
      SourceProcessingState processingState,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    private static SourceDetail from(KnowledgeSource source) {
      return new SourceDetail(
          source.id(),
          source.displayName(),
          source.contentType(),
          source.byteCount(),
          source.visibility(),
          source.processingState(),
          source.createdAt(),
          source.updatedAt(),
          source.version());
    }
  }
}
