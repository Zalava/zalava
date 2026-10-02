package org.zalava.knowledge.memory.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.zalava.knowledge.memory.domain.Memory;

/**
 * Deterministic, bounded selection of authorized memories for context injection.
 *
 * <p>Candidates are already actor- and scope-filtered by the caller; this component only ranks and
 * bounds them. Ranking is a stable lexical relevance score (query terms in text, metadata, scope
 * and provenance) with newest-first then id tie-breaks. Duplicate texts collapse to the newest
 * record. A blank query keeps every candidate newest-first, so recall is never lost when there is
 * no relevance signal. When selection is disabled the candidate order is returned unchanged, which
 * is the MEM-01 fallback.
 */
public final class DeterministicMemorySelector {

  private final boolean enabled;

  public DeterministicMemorySelector() {
    this(true);
  }

  public DeterministicMemorySelector(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean enabled() {
    return enabled;
  }

  public MemorySelection select(String query, List<Memory> candidates, int limit) {
    return select(query, candidates, limit, 0);
  }

  /**
   * Selects at most {@code limit} memories, additionally bounded by {@code characterBudget} when it
   * is positive (a value of {@code 0} leaves character bounding to the caller/aggregate budget).
   */
  public MemorySelection select(
      String query, List<Memory> candidates, int limit, int characterBudget) {
    if (characterBudget < 0) {
      throw new IllegalArgumentException("characterBudget must not be negative");
    }
    List<Memory> input =
        candidates == null ? List.of() : candidates.stream().filter(Objects::nonNull).toList();
    if (limit < 1 || input.isEmpty()) {
      return new MemorySelection(
          List.of(), new MemorySelection.Metrics(input.size(), input.size(), 0, 0, 0, 0, 0));
    }
    if (!enabled) {
      List<Memory> selected = input.stream().limit(limit).toList();
      return new MemorySelection(
          selected,
          new MemorySelection.Metrics(
              input.size(),
              input.size(),
              selected.size(),
              0,
              input.size() - selected.size(),
              0,
              characters(selected)));
    }

    List<Memory> deduplicated = deduplicate(input);
    List<String> terms = terms(query);
    List<ScoredMemory> scored = new ArrayList<>();
    int omittedByRelevance = 0;
    for (Memory memory : deduplicated) {
      int score = score(memory, terms);
      if (!terms.isEmpty() && score == 0) {
        omittedByRelevance++;
        continue;
      }
      scored.add(new ScoredMemory(memory, score));
    }
    scored.sort(SCORED_ORDER);

    List<Memory> selected = new ArrayList<>();
    int characters = 0;
    int omittedByLimit = 0;
    int omittedByBudget = 0;
    for (ScoredMemory candidate : scored) {
      if (selected.size() >= limit) {
        omittedByLimit++;
        continue;
      }
      int length = candidate.memory().text().length();
      if (characterBudget > 0 && characters + length > characterBudget) {
        omittedByBudget++;
        continue;
      }
      selected.add(candidate.memory());
      characters += length;
    }
    return new MemorySelection(
        selected,
        new MemorySelection.Metrics(
            input.size(),
            deduplicated.size(),
            selected.size(),
            omittedByRelevance,
            omittedByLimit,
            omittedByBudget,
            characters));
  }

  private static List<Memory> deduplicate(List<Memory> input) {
    Map<String, Memory> byText = new LinkedHashMap<>();
    for (Memory memory : input) {
      byText.merge(normalize(memory.text()), memory, DeterministicMemorySelector::newest);
    }
    return List.copyOf(byText.values());
  }

  private static Memory newest(Memory left, Memory right) {
    if (left.createdAt().isAfter(right.createdAt())) {
      return left;
    }
    if (left.createdAt().isBefore(right.createdAt())) {
      return right;
    }
    return left.id().compareTo(right.id()) <= 0 ? left : right;
  }

  private static int score(Memory memory, List<String> terms) {
    if (terms.isEmpty()) {
      return 0;
    }
    String text = memory.text().toLowerCase(Locale.ROOT);
    String scope = memory.scope().name().toLowerCase(Locale.ROOT);
    String source = memory.provenance().source().toLowerCase(Locale.ROOT);
    int score = 0;
    for (String term : terms) {
      if (text.contains(term)) {
        score += 3;
      }
      if (memory.metadata().values().stream()
          .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(term))) {
        score += 2;
      }
      if (scope.contains(term) || source.contains(term)) {
        score += 1;
      }
    }
    return score;
  }

  private static List<String> terms(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    List<String> terms = new ArrayList<>();
    for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
      if (!token.isBlank() && !terms.contains(token)) {
        terms.add(token);
      }
    }
    return terms;
  }

  private static String normalize(String text) {
    return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static int characters(List<Memory> memories) {
    return memories.stream().mapToInt(memory -> memory.text().length()).sum();
  }

  private static final Comparator<ScoredMemory> SCORED_ORDER =
      Comparator.comparingInt(ScoredMemory::score)
          .reversed()
          .thenComparing(ScoredMemory::createdAt, Comparator.reverseOrder())
          .thenComparing(scored -> scored.memory().id());

  private record ScoredMemory(Memory memory, int score) {
    private Instant createdAt() {
      return memory.createdAt();
    }
  }
}
