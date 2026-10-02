package org.zalava.knowledge.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.content.ContentExtractionFailureCategory;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeLibrary;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.application.port.out.KnowledgeSearchStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.DerivationState;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.platform.persistence.OptimisticLockConflictException;
import org.zalava.support.RestartableSeaApplicationContext;

@SpringBootTest
class JdbcKnowledgeSourceStoreIntegrationTest {
  private static final Path DATABASE_PATH = createDatabasePath();

  @Autowired KnowledgeSourceStore sources;
  @Autowired KnowledgeDerivationStore derivations;
  @Autowired KnowledgeExtractionRecordStore extractionRecords;
  @Autowired KnowledgeSearchStore search;
  @Autowired KnowledgeSourceLifecycle lifecycle;
  @Autowired KnowledgeLibrary library;
  @Autowired AccountLifecycle accounts;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("agent.workspace", () -> DATABASE_PATH.getParent().toUri().toString());
    registry.add("sea.accounts.security-enabled", () -> "false");
    registry.add("sea.accounts.bootstrap-login", () -> "knowledge-admin");
    registry.add("sea.accounts.bootstrap-password", () -> "KnowledgePassword-123");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void persistsPrivateSourcesAndOnlyExposesExplicitlySharedSourcesToAnotherMember() {
    var owner = accounts.create("knowledge-owner", "OwnerPassword-123", AccountRole.MEMBER);
    var other = accounts.create("knowledge-other", "OtherPassword-123", AccountRole.MEMBER);
    KnowledgeSource privateSource = source(new Actor(owner.id()), KnowledgeVisibility.PRIVATE);
    KnowledgeSource sharedSource = source(new Actor(owner.id()), KnowledgeVisibility.GROUP_SHARED);

    sources.register(privateSource);
    sources.register(sharedSource);

    assertThat(sources.findById(privateSource.id())).contains(privateSource);
    assertThat(sources.visibleTo(new Actor(other.id())))
        .extracting(KnowledgeSource::id)
        .contains(sharedSource.id())
        .doesNotContain(privateSource.id());
  }

  @Test
  void incrementsVersionAndRejectsAStaleSourceUpdate() {
    var owner = accounts.create("versioned-owner", "OwnerPassword-123", AccountRole.MEMBER);
    KnowledgeSource source = source(new Actor(owner.id()), KnowledgeVisibility.PRIVATE);
    sources.register(source);

    KnowledgeSource saved = sources.save(source.share(Instant.parse("2026-08-27T20:01:00Z")));

    assertThat(saved.version()).isEqualTo(1);
    assertThat(sources.findById(source.id())).contains(saved);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> sources.save(source.unshare(Instant.parse("2026-08-27T20:02:00Z"))))
        .isInstanceOf(OptimisticLockConflictException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> sources.delete(source))
        .isInstanceOf(OptimisticLockConflictException.class);
  }

  @Test
  void incrementsPersistenceVersionAndRejectsAStaleDerivationTransition() {
    KnowledgeDerivation candidate =
        new KnowledgeDerivation(
            KnowledgeSourceId.create(),
            1,
            "extractor",
            "1",
            DerivationState.CANDIDATE,
            Instant.parse("2026-08-27T20:00:00Z"),
            0);
    derivations.record(candidate);

    KnowledgeDerivation active =
        derivations.save(
            candidate.withState(DerivationState.ACTIVE, Instant.parse("2026-08-27T20:01:00Z")));

    assertThat(active.persistenceVersion()).isEqualTo(1);
    assertThat(derivations.active(candidate.sourceId())).contains(active);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                derivations.save(
                    candidate.withState(
                        DerivationState.FAILED, Instant.parse("2026-08-27T20:02:00Z"))))
        .isInstanceOf(OptimisticLockConflictException.class);
  }

  @Test
  void persistsBoundedSuccessfulAndTypedFailedExtractionOutcomes() {
    KnowledgeDerivation success =
        new KnowledgeDerivation(
            KnowledgeSourceId.create(),
            1,
            "tika",
            "4.0.0",
            DerivationState.CANDIDATE,
            Instant.now(),
            0);
    KnowledgeDerivation failure =
        new KnowledgeDerivation(
            KnowledgeSourceId.create(),
            1,
            "tika",
            "4.0.0",
            DerivationState.CANDIDATE,
            Instant.now(),
            0);
    derivations.record(success);
    derivations.record(failure);

    extractionRecords.record(KnowledgeExtractionRecord.succeeded(success, "extracted text"));
    extractionRecords.record(
        KnowledgeExtractionRecord.failed(
            failure, ContentExtractionFailureCategory.MALFORMED_INPUT, "Parser rejected input"));

    assertThat(extractionRecords.find(success.sourceId(), success.version()).orElseThrow().text())
        .isEqualTo("extracted text");
    assertThat(
            extractionRecords
                .find(failure.sourceId(), failure.version())
                .orElseThrow()
                .failureCategory())
        .isEqualTo(ContentExtractionFailureCategory.MALFORMED_INPUT);
  }

  @Test
  void findsOnlyActiveDerivationCandidatesThroughThePostgreSqlIndex() {
    var owner = accounts.create("postgres-search-owner", "OwnerPassword-123", AccountRole.MEMBER);
    KnowledgeSource activeSource = source(new Actor(owner.id()), KnowledgeVisibility.PRIVATE);
    KnowledgeSource replacedSource = source(new Actor(owner.id()), KnowledgeVisibility.PRIVATE);
    sources.register(activeSource);
    sources.register(replacedSource);
    KnowledgeDerivation active =
        new KnowledgeDerivation(
            activeSource.id(), 1, "tika", "1", DerivationState.CANDIDATE, Instant.now(), 0);
    KnowledgeDerivation replaced =
        new KnowledgeDerivation(
            replacedSource.id(), 1, "tika", "1", DerivationState.CANDIDATE, Instant.now(), 0);
    derivations.record(active);
    derivations.record(replaced);
    KnowledgeDerivation promoted =
        derivations.save(active.withState(DerivationState.ACTIVE, Instant.now()));
    KnowledgeDerivation retired =
        derivations.save(replaced.withState(DerivationState.REPLACED, Instant.now()));
    extractionRecords.record(KnowledgeExtractionRecord.succeeded(promoted, "renewal evidence"));
    extractionRecords.record(KnowledgeExtractionRecord.succeeded(retired, "renewal evidence"));

    assertThat(
            search.findCandidates(
                new KnowledgeSearchStore.SearchCriteria("renewal", null, null, 10)))
        .containsExactly(new KnowledgeSearchStore.Candidate(activeSource.id(), 1));
    assertThat(
            search.findCandidates(
                new KnowledgeSearchStore.SearchCriteria(
                    "source.txt", "text/plain", SourceProcessingState.PENDING, 10)))
        .containsExactly(new KnowledgeSearchStore.Candidate(activeSource.id(), 1));

    lifecycle.hardDelete(new Actor(owner.id()), activeSource.id());

    assertThat(
            search.findCandidates(
                new KnowledgeSearchStore.SearchCriteria("renewal", null, null, 10)))
        .isEmpty();
  }

  @Test
  void findsPersistedActiveEvidenceAndRemovesItAfterDeletionFromAFreshApplicationContext() {
    var owner = accounts.create("postgres-restart-owner", "OwnerPassword-123", AccountRole.MEMBER);
    KnowledgeSource source = source(new Actor(owner.id()), KnowledgeVisibility.PRIVATE);
    sources.register(source);
    KnowledgeDerivation candidate =
        derivations.record(
            new KnowledgeDerivation(
                source.id(), 1, "extractor", "1", DerivationState.CANDIDATE, Instant.now(), 0));
    KnowledgeDerivation active =
        derivations.save(candidate.withState(DerivationState.ACTIVE, Instant.now()));
    extractionRecords.record(KnowledgeExtractionRecord.succeeded(active, "restart evidence"));

    try (ConfigurableApplicationContext restarted =
        RestartableSeaApplicationContext.start(DATABASE_PATH.getParent())) {
      KnowledgeSearchStore restartedSearch = restarted.getBean(KnowledgeSearchStore.class);
      KnowledgeSourceLifecycle restartedLifecycle =
          restarted.getBean(KnowledgeSourceLifecycle.class);

      assertThat(
              restartedSearch.findCandidates(
                  new KnowledgeSearchStore.SearchCriteria("restart", null, null, 10)))
          .containsExactly(new KnowledgeSearchStore.Candidate(source.id(), 1));

      restartedLifecycle.hardDelete(new Actor(owner.id()), source.id());

      assertThat(
              restartedSearch.findCandidates(
                  new KnowledgeSearchStore.SearchCriteria("restart", null, null, 10)))
          .isEmpty();
    }
  }

  @Test
  void librarySearchDoesNotLetPrivateNativeCandidatesStarveAnAuthorizedResult() {
    var owner =
        accounts.create(
            "library-owner-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "OwnerPassword-123",
            AccountRole.MEMBER);
    var other =
        accounts.create(
            "library-other-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "OtherPassword-123",
            AccountRole.MEMBER);
    Actor ownerActor = new Actor(owner.id());
    String token = "starvetoken" + java.util.UUID.randomUUID().toString().replace("-", "");
    KnowledgeSourceId authorized = active(ownerActor, token + " authorized household note");
    for (int index = 0; index < 25; index++) {
      active(new Actor(other.id()), token + " PRIVATE_CONTENT " + index);
    }

    assertThat(library.search(ownerActor, token, KnowledgeLibrary.MetadataFilter.none(), 1))
        .extracting(KnowledgeLibrary.SourceSummary::id)
        .containsExactly(authorized);
  }

  private KnowledgeSourceId active(Actor owner, String text) {
    Instant created =
        Instant.parse("2026-08-27T20:00:00Z").plusSeconds(ACTIVE_SEQUENCE.incrementAndGet());
    KnowledgeSource source =
        new KnowledgeSource(
            KnowledgeSourceId.create(),
            owner,
            "active-" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".txt",
            "text/plain",
            text.length(),
            "a".repeat(64),
            KnowledgeVisibility.PRIVATE,
            SourceProcessingState.PENDING,
            created,
            created,
            0);
    sources.register(source);
    KnowledgeDerivation candidate =
        derivations.record(
            new KnowledgeDerivation(
                source.id(), 1, "fixture", "1", DerivationState.CANDIDATE, created, 0));
    KnowledgeDerivation active =
        derivations.save(candidate.withState(DerivationState.ACTIVE, created));
    extractionRecords.record(KnowledgeExtractionRecord.succeeded(active, text));
    return source.id();
  }

  private static final java.util.concurrent.atomic.AtomicLong ACTIVE_SEQUENCE =
      new java.util.concurrent.atomic.AtomicLong();

  private static KnowledgeSource source(Actor owner, KnowledgeVisibility visibility) {
    Instant now = Instant.parse("2026-08-27T20:00:00Z");
    return new KnowledgeSource(
        KnowledgeSourceId.create(),
        owner,
        "source.txt",
        "text/plain",
        7,
        "a".repeat(64),
        visibility,
        SourceProcessingState.PENDING,
        now,
        now,
        0);
  }

  private static Path createDatabasePath() {
    try {
      Path directory = Files.createTempDirectory("jdbc-knowledge-store-integration-");
      Files.writeString(directory.resolve("AGENT.md"), "Integration test agent prompt.");
      Files.writeString(directory.resolve("INFO.md"), "Integration test environment info.");
      return directory.resolve("sea");
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
