package org.zalava.knowledge.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.knowledge.memory.domain.Memory;
import org.zalava.knowledge.memory.domain.MemoryScope;

class DeterministicMemorySelectorTest {

  private final DeterministicMemorySelector selector = new DeterministicMemorySelector();

  @Test
  void ranksByLexicalRelevanceThenRecency() {
    Memory both = memory("both", "shopping list items", 1);
    Memory one = memory("one", "shopping cart totals", 2);
    Memory none = memory("none", "weather forecast", 3);

    MemorySelection selection = selector.select("shopping list", List.of(none, one, both), 10);

    assertThat(selection.selected()).extracting(Memory::id).containsExactly("both", "one");
    assertThat(selection.metrics().candidates()).isEqualTo(3);
    assertThat(selection.metrics().afterDeduplication()).isEqualTo(3);
    assertThat(selection.metrics().selected()).isEqualTo(2);
    assertThat(selection.metrics().omittedByRelevance()).isEqualTo(1);
    assertThat(selection.metrics().omittedByLimit()).isZero();
    assertThat(selection.metrics().omittedByBudget()).isZero();
    assertThat(selection.metrics().characters())
        .isEqualTo("shopping list items".length() + "shopping cart totals".length());
  }

  @Test
  void omittedRelevanceCandidatesAreReportedButNotInjected() {
    Memory unrelated = memory("unrelated", "weather forecast", 1);

    MemorySelection selection = selector.select("shopping", List.of(unrelated), 10);

    assertThat(selection.selected()).isEmpty();
    assertThat(selection.metrics().candidates()).isEqualTo(1);
    assertThat(selection.metrics().omittedByRelevance()).isEqualTo(1);
  }

  @Test
  void collapsesDuplicateTextsKeepingTheNewest() {
    Memory older = memory("older", "same fact", 1);
    Memory newer = memory("newer", "same fact", 5);

    MemorySelection selection = selector.select("fact", List.of(newer, older), 10);

    assertThat(selection.selected()).extracting(Memory::id).containsExactly("newer");
    assertThat(selection.metrics().candidates()).isEqualTo(2);
    assertThat(selection.metrics().afterDeduplication()).isEqualTo(1);
    assertThat(selection.metrics().omittedByLimit()).isZero();
  }

  @Test
  void respectsLimitAndReportsOmittedByLimit() {
    Memory first = memory("first", "shopping apples", 3);
    Memory second = memory("second", "shopping pears", 2);
    Memory third = memory("third", "shopping plums", 1);

    MemorySelection selection = selector.select("shopping", List.of(first, second, third), 2);

    assertThat(selection.selected()).hasSize(2);
    assertThat(selection.metrics().selected()).isEqualTo(2);
    assertThat(selection.metrics().omittedByLimit()).isEqualTo(1);
  }

  @Test
  void respectsAnExplicitCharacterBudget() {
    Memory big = memory("big", "shopping list ".repeat(5).strip(), 2);
    Memory small = memory("small", "shopping", 1);

    MemorySelection selection = selector.select("shopping", List.of(big, small), 10, 10);

    assertThat(selection.selected()).extracting(Memory::id).containsExactly("small");
    assertThat(selection.metrics().omittedByBudget()).isEqualTo(1);
    assertThat(selection.metrics().characters()).isLessThanOrEqualTo(10);
  }

  @Test
  void blankQueryKeepsEveryCandidateNewestFirst() {
    Memory older = memory("older", "anything", 1);
    Memory newer = memory("newer", "anything else", 5);

    MemorySelection selection = selector.select(null, List.of(older, newer), 10);

    assertThat(selection.selected()).extracting(Memory::id).containsExactly("newer", "older");
    assertThat(selection.metrics().omittedByRelevance()).isZero();
  }

  @Test
  void disabledSelectionReturnsCandidateOrderUnchanged() {
    Memory first = memory("first", "irrelevant", 3);
    Memory second = memory("second", "shopping", 2);

    MemorySelection selection =
        new DeterministicMemorySelector(false).select("shopping", List.of(first, second), 10);

    assertThat(selection.selected()).extracting(Memory::id).containsExactly("first", "second");
    assertThat(selection.metrics().omittedByRelevance()).isZero();
    assertThat(selection.metrics().omittedByLimit()).isZero();
  }

  @Test
  void selectionIsDeterministicAcrossRepeatedCalls() {
    List<Memory> candidates =
        List.of(
            memory("a", "shopping list", 1),
            memory("b", "shopping list", 1),
            memory("c", "shopping", 9),
            memory("d", "list", 4));

    MemorySelection first = selector.select("shopping list", candidates, 3);
    MemorySelection second = selector.select("shopping list", candidates, 3);

    assertThat(first.selected()).isEqualTo(second.selected());
    assertThat(first.metrics()).isEqualTo(second.metrics());
  }

  @Test
  void metadataScopeAndProvenanceContributeToRelevance() {
    Memory tagged =
        new Memory(
            "tagged",
            MemoryScope.PROJECT,
            "deployment checklist",
            Map.of("topic", "shopping"),
            Instant.ofEpochSecond(1));
    Memory untagged = memory("untagged", "deployment notes", 5);

    MemorySelection selection = selector.select("shopping", List.of(untagged, tagged), 10);

    assertThat(selection.selected()).extracting(Memory::id).containsExactly("tagged");
  }

  @Test
  void invalidInputsAndMetricsBoundsAreRejected() {
    assertThatThrownBy(() -> selector.select("q", List.of(), 10, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("characterBudget must not be negative");
    assertThat(selector.select("q", List.of(), 10).selected()).isEmpty();
    assertThat(selector.select("q", List.of(memory("m", "shopping", 1)), 0).selected()).isEmpty();

    assertThatThrownBy(() -> new MemorySelection.Metrics(-1, 0, 0, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MemorySelection.Metrics(2, 1, 2, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new MemorySelection(List.of(), new MemorySelection.Metrics(1, 1, 1, 0, 0, 0, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("metrics.selected");
  }

  private static Memory memory(String id, String text, long createdAtSecond) {
    return new Memory(
        id, MemoryScope.PROJECT, text, Map.of(), Instant.ofEpochSecond(createdAtSecond));
  }
}
