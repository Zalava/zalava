package org.zalava.memory.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.application.ContextSourceBudgets;
import org.zalava.agent.application.DefaultAgentContextAssembler;
import org.zalava.agent.domain.AgentContext;
import org.zalava.memory.adapter.out.filesystem.FileSystemMemoryStore;
import org.zalava.memory.application.ActorBoundMemoryQueries;
import org.zalava.memory.application.DeterministicMemorySelector;
import org.zalava.memory.application.MemorySelection;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryProvenance;
import org.zalava.memory.domain.MemoryScope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opt-in MEM-02 measurement/acceptance lane.
 *
 * <p>Runs the real actor-bound queries over a filesystem corpus and prints {@code MEM-METRICS}
 * lines that the MEM-02 plan records verbatim. It is excluded from {@code :app:check} and runs
 * through {@code memorySelectionMeasurementTest}.
 */
@Tag("memory-selection-measurement")
class MemorySelectionAcceptanceTest {

  private static final String MEMORY_DIRECTORY =
      "SEA keeps durable memory under users/<account-id>/memory";
  private static final String FOCUSED_TESTS =
      "Prefer focused regression tests for every reproduced defect";
  private static final String STYLE = "The user prefers concise answers with short sentences";
  private static final String WEATHER = "The weather in Madrid is sunny and warm";
  private static final String EXECUTION_TRACE = "invoked the shopping list add tool";

  @Test
  void recordsDeterministicSelectionMetricsForRecordedQueries(@TempDir Path workspace)
      throws InterruptedException {
    RecordedCorpus corpus = corpus(workspace);

    List<String> queries =
        List.of("memory directory durable", "focused tests", "shopping", "Madrid weather");
    for (String query : queries) {
      MemorySelection selection = corpus.query(query);
      printMetrics(query, selection);
    }

    MemorySelection directory = corpus.query("memory directory durable");
    assertThat(texts(directory)).contains(MEMORY_DIRECTORY).doesNotContain(WEATHER);

    MemorySelection focused = corpus.query("focused tests");
    assertThat(texts(focused)).contains(FOCUSED_TESTS);

    MemorySelection shopping = corpus.query("shopping");
    assertThat(shopping.selected()).isEmpty();
    assertThat(shopping.metrics().omittedByRelevance())
        .isEqualTo(shopping.metrics().afterDeduplication());

    MemorySelection weather = corpus.query("Madrid weather");
    assertThat(texts(weather)).contains(WEATHER).doesNotContain(MEMORY_DIRECTORY);

    MemorySelection first = corpus.query("memory directory durable");
    MemorySelection second = corpus.query("memory directory durable");
    assertThat(second.selected()).isEqualTo(first.selected());
    assertThat(second.metrics()).isEqualTo(first.metrics());

    Set<String> owned = corpus.actorMemoryTexts();
    assertThat(texts(directory)).allMatch(owned::contains);
    assertThat(texts(focused)).allMatch(owned::contains);
    assertThat(texts(weather)).allMatch(owned::contains);
  }

  @Test
  void disabledSelectionFallsBackToCandidateOrder(@TempDir Path workspace)
      throws InterruptedException {
    RecordedCorpus corpus = corpus(workspace);
    ActorBoundMemoryQueries disabled =
        new ActorBoundMemoryQueries(
            corpus.store(), corpus.actors(), new DeterministicMemorySelector(false), 50);

    List<Memory> selected =
        corpus
            .actors()
            .call(corpus.actor(), AccountRole.MEMBER, () -> disabled.search("memory", 10));

    assertThat(selected).isNotEmpty();
    assertThat(selected)
        .isSortedAccordingTo((left, right) -> right.createdAt().compareTo(left.createdAt()));
  }

  @Test
  void contextBudgetInteractionBoundsInjectedMemoryCharacters(@TempDir Path workspace)
      throws InterruptedException {
    RecordedCorpus corpus = corpus(workspace);
    DefaultAgentContextAssembler assembler =
        new DefaultAgentContextAssembler(
            8_000, 10, corpus.queries(), null, new ContextSourceBudgets(0, 0, 40, 0));

    AgentContext context =
        corpus
            .actors()
            .call(
                corpus.actor(),
                AccountRole.MEMBER,
                () -> assembler.assemble("memory directory durable", null));

    AgentContext.SourceMetric metric = memoryMetric(context);
    assertThat(metric.charactersUsed()).isLessThanOrEqualTo(40);
    assertThat(metric.charactersAvailable()).isGreaterThanOrEqualTo(metric.charactersUsed());
  }

  private static AgentContext.SourceMetric memoryMetric(AgentContext context) {
    return context.sourceMetrics().stream()
        .filter(source -> source.sourceType().equals("selected_memories"))
        .findFirst()
        .orElseThrow();
  }

  private static void printMetrics(String query, MemorySelection selection) {
    MemorySelection.Metrics metrics = selection.metrics();
    System.out.printf(
        "MEM-METRICS query=%s candidates=%d dedup=%d selected=%d omittedRelevance=%d "
            + "omittedLimit=%d omittedBudget=%d chars=%d%n",
        query,
        metrics.candidates(),
        metrics.afterDeduplication(),
        metrics.selected(),
        metrics.omittedByRelevance(),
        metrics.omittedByLimit(),
        metrics.omittedByBudget(),
        metrics.characters());
  }

  private static Set<String> texts(MemorySelection selection) {
    return selection.selected().stream().map(Memory::text).collect(Collectors.toSet());
  }

  private static RecordedCorpus corpus(Path workspace) throws InterruptedException {
    FileSystemMemoryStore store = new FileSystemMemoryStore(workspace);
    ActorExecutionContext actors = new ActorExecutionContext();
    Actor actor = new Actor(AccountId.newId());
    Actor other = new Actor(AccountId.newId());

    store.remember(
        actor, new MemoryDraft(MemoryScope.USER, STYLE, java.util.Map.of("topic", "style")));
    store.remember(
        actor, new MemoryDraft(MemoryScope.PROJECT, MEMORY_DIRECTORY, java.util.Map.of()));
    store.remember(
        actor, new MemoryDraft(MemoryScope.PROJECT, WEATHER, java.util.Map.of("place", "Madrid")));
    store.remember(actor, new MemoryDraft(MemoryScope.AGENT, FOCUSED_TESTS, java.util.Map.of()));
    Thread.sleep(5);
    store.remember(actor, new MemoryDraft(MemoryScope.AGENT, FOCUSED_TESTS, java.util.Map.of()));
    store.remember(
        actor,
        new MemoryDraft(
            MemoryScope.EXECUTION,
            EXECUTION_TRACE,
            java.util.Map.of(),
            MemoryProvenance.of("task", "run-1")));
    store.remember(
        other, new MemoryDraft(MemoryScope.PROJECT, MEMORY_DIRECTORY, java.util.Map.of()));

    ActorBoundMemoryQueries queries =
        new ActorBoundMemoryQueries(store, actors, new DeterministicMemorySelector(), 50);
    Set<String> actorMemoryTexts =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () ->
                store.recent(actor, MemoryScope.all(), 50).stream()
                    .map(Memory::text)
                    .collect(Collectors.toSet()));
    return new RecordedCorpus(store, actors, actor, queries, actorMemoryTexts);
  }

  private record RecordedCorpus(
      FileSystemMemoryStore store,
      ActorExecutionContext actors,
      Actor actor,
      ActorBoundMemoryQueries queries,
      Set<String> actorMemoryTexts) {

    MemorySelection query(String query) {
      return actors.call(actor, AccountRole.MEMBER, () -> queries.selection(query, 10));
    }
  }
}
