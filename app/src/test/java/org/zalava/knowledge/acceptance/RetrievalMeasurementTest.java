package org.zalava.knowledge.acceptance;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeEvidenceQueries;
import org.zalava.knowledge.application.KnowledgeLibrary;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.domain.KnowledgeEvidence;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.support.RestartableSeaApplicationContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in permission-safe retrieval measurement lane for KNOW-SEM-01.
 *
 * <p>Seeds a small synthetic household corpus across two actors and two visibility scopes against a
 * real PostgreSQL-backed SEA, then measures lexical relevance/ranking through the citation path
 * ({@link KnowledgeEvidenceQueries}) and isolation across restart, revocation and deletion. It also
 * probes authorization-before-ranking with many private candidates preceding one authorized
 * candidate.
 *
 * <p>It prints {@code RETRIEVAL-METRICS} lines that the KNOW-SEM-01 plan records verbatim. It is
 * excluded from {@code :app:check} and runs through {@code retrievalMeasurementTest}.
 */
@Tag("retrieval-measurement")
class RetrievalMeasurementTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int RESULT_LIMIT = 5;
  private static final int STARVATION_DISTRACTORS = 25;
  private static final Map<String, String> DATABASE_OVERRIDE =
      Map.of(
          "spring.datasource.url",
          "jdbc:tc:postgresql:18.4-alpine:///sea_retrieval_"
              + UUID.randomUUID().toString().replace("-", "")
              + "?TC_DAEMON=true");

  @Test
  void measuresLexicalRelevanceRankingAndIsolationAcrossRestartRevocationAndDeletion()
      throws Exception {
    Path workspace = createWorkspace();
    JsonNode corpus = readCorpus();
    Map<String, String> actorLogins = new LinkedHashMap<>();
    Map<String, DocumentSpec> byLabel = new LinkedHashMap<>();
    Map<UUID, String> labelBySource = new LinkedHashMap<>();
    Map<String, KnowledgeSourceId> sourceByLabel = new LinkedHashMap<>();

    ConfigurableApplicationContext first =
        RestartableSeaApplicationContext.start(workspace, DATABASE_OVERRIDE);
    Map<String, Actor> actors = new LinkedHashMap<>();
    try {
      for (String key : List.of("alice", "bob")) {
        actorLogins.put(key, "retrieval-" + key + "-" + shortId());
      }
      for (Map.Entry<String, String> entry : actorLogins.entrySet()) {
        actors.put(
            entry.getKey(),
            new Actor(
                first
                    .getBean(AccountLifecycle.class)
                    .create(entry.getValue(), "FixturePassword-123", AccountRole.MEMBER)
                    .id()));
      }

      KnowledgeSourceLifecycle lifecycle = first.getBean(KnowledgeSourceLifecycle.class);
      KnowledgeExtractionRecordStore records = first.getBean(KnowledgeExtractionRecordStore.class);
      for (JsonNode node : corpus.path("documents")) {
        DocumentSpec document = document(node);
        byLabel.put(document.label(), document);
        KnowledgeSourceId id = seed(lifecycle, records, actors.get(document.actor()), document);
        sourceByLabel.put(document.label(), id);
        labelBySource.put(id.value(), document.label());
      }

      KnowledgeEvidenceQueries evidence = first.getBean(KnowledgeEvidenceQueries.class);
      KnowledgeLibrary library = first.getBean(KnowledgeLibrary.class);

      List<QuerySpec> queries = queries(corpus.path("queries"));
      List<QueryResult> initial = measure(evidence, actors, byLabel, labelBySource, queries);
      printRelevance(initial);

      int privateLeaks = privateExclusion(evidence, corpus, actors, byLabel, labelBySource);
      System.out.println("RETRIEVAL-METRICS isolation=private-exclusion leaks=" + privateLeaks);

      boolean revokedVisibleBefore = false;
      int revocationLeaks = 0;
      int routerSeenAfterUnshare = 0;
      KnowledgeSourceId router = sourceByLabel.get("router");
      Actor alice = actors.get("alice");
      Actor bob = actors.get("bob");
      KnowledgeSourceId sharedRouter = router;
      revokedVisibleBefore =
          contains(evidence.search(bob, "router reset", RESULT_LIMIT), sharedRouter);
      lifecycle.changeVisibility(alice, router, KnowledgeVisibility.PRIVATE);
      routerSeenAfterUnshare =
          contains(evidence.search(bob, "router reset", RESULT_LIMIT), sharedRouter) ? 1 : 0;
      revocationLeaks = revokedVisibleBefore && routerSeenAfterUnshare == 0 ? 0 : 1;
      System.out.println(
          "RETRIEVAL-METRICS isolation=revocation visible-before-unshare="
              + revokedVisibleBefore
              + " visible-after-unshare="
              + (routerSeenAfterUnshare == 1)
              + " leaks="
              + revocationLeaks);
      if (!revokedVisibleBefore || routerSeenAfterUnshare != 0) revocationLeaks = 1;

      String starvationToken = "starvetoken" + shortId();
      KnowledgeSourceId authorized =
          seed(
              lifecycle,
              records,
              alice,
              new DocumentSpec(
                  "starvation-authorized",
                  "alice",
                  KnowledgeVisibility.PRIVATE,
                  "starvation-authorized.txt",
                  starvationToken + " authorized household note"));
      for (int index = 0; index < STARVATION_DISTRACTORS; index++) {
        seed(
            lifecycle,
            records,
            bob,
            new DocumentSpec(
                "starvation-private-" + index,
                "bob",
                KnowledgeVisibility.PRIVATE,
                "starvation-private-" + index + ".txt",
                starvationToken + " PRIVATE_CONTENT " + index));
      }
      List<KnowledgeEvidence> authorizedEvidence = evidence.search(alice, starvationToken, 1);
      int evidenceStarved =
          authorizedEvidence.size() == 1
                  && authorizedEvidence.get(0).sourceId().equals(authorized.value())
              ? 0
              : 1;
      List<KnowledgeLibrary.SourceSummary> libraryCandidates =
          library.search(alice, starvationToken, KnowledgeLibrary.MetadataFilter.none(), 1);
      int libraryStarved = libraryCandidates.isEmpty() ? 1 : 0;
      System.out.println(
          "RETRIEVAL-METRICS isolation=authorization-before-ranking evidence-starved="
              + evidenceStarved
              + " library-starved="
              + libraryStarved
              + " distractors="
              + STARVATION_DISTRACTORS);

      Map<String, List<String>> preRestart = snapshot(evidence, actors, labelBySource, queries);
      first.close();
      first = null;

      ConfigurableApplicationContext restarted =
          RestartableSeaApplicationContext.start(workspace, DATABASE_OVERRIDE);
      try {
        KnowledgeEvidenceQueries restartedEvidence =
            restarted.getBean(KnowledgeEvidenceQueries.class);
        Map<String, List<String>> postRestart =
            snapshot(restartedEvidence, actors, labelBySource, queries);
        int drift = 0;
        for (String key : preRestart.keySet()) {
          if (!preRestart.get(key).equals(postRestart.get(key))) drift++;
        }
        System.out.println("RETRIEVAL-METRICS isolation=restart query-drift=" + drift);

        KnowledgeSourceLifecycle restartedLifecycle =
            restarted.getBean(KnowledgeSourceLifecycle.class);
        KnowledgeDerivationStore derivations = restarted.getBean(KnowledgeDerivationStore.class);
        KnowledgeSourceId recipe = sourceByLabel.get("recipe");
        restartedLifecycle.hardDelete(alice, recipe);
        int deletionLeaks = 0;
        if (contains(restartedEvidence.search(bob, "pizza dough", RESULT_LIMIT), recipe)) {
          deletionLeaks++;
        }
        if (restartedEvidence.source(alice, recipe.value().toString()).isPresent()
            || restartedEvidence.source(bob, recipe.value().toString()).isPresent()) {
          deletionLeaks++;
        }
        if (derivations.active(recipe).isPresent()) deletionLeaks++;
        System.out.println("RETRIEVAL-METRICS isolation=deletion leaks=" + deletionLeaks);

        org.assertj.core.api.Assertions.assertThat(privateLeaks)
            .as("private candidates must never appear in another actor's citations")
            .isZero();
        org.assertj.core.api.Assertions.assertThat(revocationLeaks)
            .as("unshared sources must disappear before the next query")
            .isZero();
        org.assertj.core.api.Assertions.assertThat(evidenceStarved)
            .as("authorized candidates must not be starved by private candidates")
            .isZero();
        org.assertj.core.api.Assertions.assertThat(drift)
            .as("restart must not change authorization-scoped results")
            .isZero();
        org.assertj.core.api.Assertions.assertThat(deletionLeaks)
            .as("deleted sources must not appear or remain active")
            .isZero();
        org.assertj.core.api.Assertions.assertThat(libraryStarved)
            .as("the library search must filter authorization before the candidate limit")
            .isZero();

        assertRelevance(initial);
      } finally {
        restarted.close();
      }
    } finally {
      if (first != null) first.close();
    }
  }

  private static void assertRelevance(List<QueryResult> results) {
    double exactRecall = recallFor(results, "exact");
    double exactMrr = mrrFor(results, "exact");
    double keywordRecall = recallFor(results, "keyword");
    double keywordMrr = mrrFor(results, "keyword");
    double paraphraseRecall = recallFor(results, "paraphrase");
    org.assertj.core.api.Assertions.assertThat(exactRecall)
        .as("exact identifier queries must resolve lexically")
        .isEqualTo(1.0);
    org.assertj.core.api.Assertions.assertThat(exactMrr).isEqualTo(1.0);
    org.assertj.core.api.Assertions.assertThat(keywordRecall)
        .as("in-contract keyword queries must resolve lexically")
        .isEqualTo(1.0);
    org.assertj.core.api.Assertions.assertThat(keywordMrr).isEqualTo(1.0);
    org.assertj.core.api.Assertions.assertThat(paraphraseRecall)
        .as("synonym-only queries are the measured lexical limitation")
        .isLessThan(1.0);
  }

  private List<QueryResult> measure(
      KnowledgeEvidenceQueries evidence,
      Map<String, Actor> actors,
      Map<String, DocumentSpec> byLabel,
      Map<UUID, String> labelBySource,
      List<QuerySpec> queries) {
    List<QueryResult> results = new ArrayList<>();
    for (QuerySpec query : queries) {
      Actor actor = actors.get(query.actor());
      List<String> retrieved =
          mapLabels(evidence.search(actor, query.text(), RESULT_LIMIT), labelBySource);
      Set<String> unauthorized = new LinkedHashSet<>();
      for (String label : retrieved) {
        DocumentSpec document = byLabel.get(label);
        if (document == null) continue;
        if (!document.actor().equals(query.actor())
            && document.visibility() == KnowledgeVisibility.PRIVATE) {
          unauthorized.add(label);
        }
      }
      results.add(
          new QueryResult(
              query.label(),
              query.category(),
              retrieved,
              query.expect(),
              query.primary(),
              unauthorized));
    }
    return results;
  }

  private void printRelevance(List<QueryResult> results) {
    for (String category : List.of("exact", "keyword", "paraphrase", "ranking")) {
      List<QueryResult> group =
          results.stream().filter(result -> result.category().equals(category)).toList();
      if (group.isEmpty()) continue;
      double recall = recallFor(results, category);
      double mrr = mrrFor(results, category);
      StringBuilder line =
          new StringBuilder(
              String.format(
                  Locale.ROOT,
                  "RETRIEVAL-METRICS category=%s queries=%d recall@%d=%.3f mrr=%.3f",
                  category,
                  group.size(),
                  RESULT_LIMIT,
                  recall,
                  mrr));
      if (category.equals("ranking")) {
        line.append(
            String.format(
                Locale.ROOT,
                " primary@1=%.3f primary-mrr=%.3f",
                primaryAt1(group),
                primaryMrr(group)));
      }
      line.append(" leaks=").append(group.stream().mapToInt(r -> r.unauthorized().size()).sum());
      System.out.println(line);
    }
    for (QueryResult result : results) {
      System.out.println(
          "RETRIEVAL-METRICS query="
              + result.label()
              + " category="
              + result.category()
              + " retrieved="
              + result.retrieved()
              + " expect="
              + result.expect());
    }
  }

  private static double recallFor(List<QueryResult> results, String category) {
    double total = 0;
    int count = 0;
    for (QueryResult result : results) {
      if (!result.category().equals(category) || result.expect().isEmpty()) continue;
      Set<String> expected = new LinkedHashSet<>(result.expect());
      expected.retainAll(result.retrieved());
      total += (double) expected.size() / result.expect().size();
      count++;
    }
    return count == 0 ? 1.0 : total / count;
  }

  private static double mrrFor(List<QueryResult> results, String category) {
    double total = 0;
    int count = 0;
    for (QueryResult result : results) {
      if (!result.category().equals(category) || result.expect().isEmpty()) continue;
      total += reciprocalRank(result.retrieved(), new LinkedHashSet<>(result.expect()));
      count++;
    }
    return count == 0 ? 0.0 : total / count;
  }

  private static double primaryAt1(List<QueryResult> group) {
    int hits = 0;
    for (QueryResult result : group) {
      if (result.primary() != null
          && !result.retrieved().isEmpty()
          && result.retrieved().get(0).equals(result.primary())) {
        hits++;
      }
    }
    return (double) hits / group.size();
  }

  private static double primaryMrr(List<QueryResult> group) {
    double total = 0;
    for (QueryResult result : group) {
      if (result.primary() != null) {
        total += reciprocalRank(result.retrieved(), Set.of(result.primary()));
      }
    }
    return total / group.size();
  }

  private static double reciprocalRank(List<String> retrieved, Set<String> relevant) {
    for (int index = 0; index < retrieved.size(); index++) {
      if (relevant.contains(retrieved.get(index))) return 1.0 / (index + 1);
    }
    return 0.0;
  }

  private int privateExclusion(
      KnowledgeEvidenceQueries evidence,
      JsonNode corpus,
      Map<String, Actor> actors,
      Map<String, DocumentSpec> byLabel,
      Map<UUID, String> labelBySource) {
    int leaks = 0;
    for (JsonNode node : corpus.path("private_exclusion")) {
      String actorKey = node.path("actor").asString();
      List<String> retrieved =
          mapLabels(
              evidence.search(actors.get(actorKey), node.path("text").asString(), RESULT_LIMIT),
              labelBySource);
      for (String label : retrieved) {
        DocumentSpec document = byLabel.get(label);
        if (document != null
            && !document.actor().equals(actorKey)
            && document.visibility() == KnowledgeVisibility.PRIVATE) {
          leaks++;
        }
      }
      if (!retrieved.isEmpty()) leaks++;
    }
    return leaks;
  }

  private Map<String, List<String>> snapshot(
      KnowledgeEvidenceQueries evidence,
      Map<String, Actor> actors,
      Map<UUID, String> labelBySource,
      List<QuerySpec> queries) {
    Map<String, List<String>> snapshot = new LinkedHashMap<>();
    for (QuerySpec query : queries) {
      snapshot.put(
          query.label(),
          mapLabels(
              evidence.search(actors.get(query.actor()), query.text(), RESULT_LIMIT),
              labelBySource));
    }
    return snapshot;
  }

  private static KnowledgeSourceId seed(
      KnowledgeSourceLifecycle lifecycle,
      KnowledgeExtractionRecordStore records,
      Actor owner,
      DocumentSpec document) {
    var source =
        lifecycle.register(
            owner, document.displayName(), "text/plain", document.text().getBytes(UTF_8));
    var candidate = lifecycle.beginReprocessing(owner, source.id(), "retrieval-fixture", "1");
    records.record(KnowledgeExtractionRecord.succeeded(candidate, document.text()));
    lifecycle.completeReprocessing(owner, candidate, true);
    if (document.visibility() == KnowledgeVisibility.GROUP_SHARED) {
      lifecycle.changeVisibility(owner, source.id(), KnowledgeVisibility.GROUP_SHARED);
    }
    sleepForOrdering();
    return source.id();
  }

  private static List<String> mapLabels(
      List<KnowledgeEvidence> evidence, Map<UUID, String> labelBySource) {
    return evidence.stream()
        .map(value -> labelBySource.getOrDefault(value.sourceId(), "unknown:" + value.sourceId()))
        .toList();
  }

  private static boolean contains(List<KnowledgeEvidence> evidence, KnowledgeSourceId id) {
    return evidence.stream().anyMatch(value -> value.sourceId().equals(id.value()));
  }

  private static List<QuerySpec> queries(JsonNode nodes) {
    List<QuerySpec> queries = new ArrayList<>();
    for (JsonNode node : nodes) {
      List<String> expect = new ArrayList<>();
      for (JsonNode value : node.path("expect")) expect.add(value.asString());
      queries.add(
          new QuerySpec(
              node.path("label").asString(),
              node.path("actor").asString(),
              node.path("text").asString(),
              node.path("category").asString(),
              expect,
              node.path("primary").isMissingNode() ? null : node.path("primary").asString()));
    }
    return queries;
  }

  private static DocumentSpec document(JsonNode node) {
    return new DocumentSpec(
        node.path("label").asString(),
        node.path("actor").asString(),
        KnowledgeVisibility.valueOf(node.path("visibility").asString()),
        node.path("displayName").asString(),
        node.path("text").asString());
  }

  private static JsonNode readCorpus() throws IOException {
    try (var input =
        RetrievalMeasurementTest.class.getResourceAsStream("/knowledge/retrieval-corpus.json")) {
      org.assertj.core.api.Assertions.assertThat(input)
          .as("retrieval corpus fixture must be on the test classpath")
          .isNotNull();
      return JSON.readTree(input);
    }
  }

  private static void sleepForOrdering() {
    try {
      Thread.sleep(Duration.ofMillis(4));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-retrieval-measurement-");
      Files.writeString(workspace.resolve("AGENT.md"), "Retrieval measurement workspace.");
      Files.writeString(workspace.resolve("INFO.md"), "Disposable measurement workspace.");
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private record DocumentSpec(
      String label,
      String actor,
      KnowledgeVisibility visibility,
      String displayName,
      String text) {}

  private record QuerySpec(
      String label,
      String actor,
      String text,
      String category,
      List<String> expect,
      String primary) {}

  private record QueryResult(
      String label,
      String category,
      List<String> retrieved,
      List<String> expect,
      String primary,
      Set<String> unauthorized) {}
}
