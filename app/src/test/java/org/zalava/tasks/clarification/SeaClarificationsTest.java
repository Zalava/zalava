package org.zalava.tasks.clarification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.clarification.adapter.out.filesystem.FileSystemClarificationRequestStore;
import org.zalava.tasks.clarification.application.port.out.ClarificationStore;
import org.zalava.tasks.clarification.domain.ClarificationDraft;
import org.zalava.tasks.clarification.domain.ClarificationRequest;
import org.zalava.tasks.domain.ActorTaskReference;

class SeaClarificationsTest {

  private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

  private final Actor owner = new Actor(AccountId.newId());
  private final Actor other = new Actor(AccountId.newId());
  private final ActorTaskReference task = ActorTaskReference.newReference();
  private final AtomicReference<Instant> clock = new AtomicReference<>(NOW);
  private final InMemoryStore store = new InMemoryStore();
  private final SeaClarifications clarifications = new SeaClarifications(store, clock::get);

  @TempDir Path workspace;

  @Test
  void ownerCanCreateAndReadATypedClarification() {
    ClarificationRequest created = clarifications.create(owner, task, draft());

    assertThat(created.status()).isEqualTo(ClarificationRequest.Status.PENDING);
    assertThat(created.expiresAt()).isEqualTo(NOW.plus(SeaClarifications.DEFAULT_TTL).toString());
    assertThat(clarifications.get(owner, created.requestId())).isEqualTo(created);
    assertThat(clarifications.pendingFor(owner, task)).containsExactly(created);
    assertThat(store.load()).containsExactly(created);
  }

  @Test
  void anotherActorCannotReadOrAnswerTheOwnersClarification() {
    ClarificationRequest created = clarifications.create(owner, task, draft());

    assertThatThrownBy(() -> clarifications.get(other, created.requestId()))
        .isInstanceOf(SeaClarifications.NotFoundException.class);
    assertThatThrownBy(() -> clarifications.answer(other, created.requestId(), answer("a")))
        .isInstanceOf(SeaClarifications.NotFoundException.class);
    assertThatThrownBy(() -> clarifications.cancel(other, created.requestId()))
        .isInstanceOf(SeaClarifications.NotFoundException.class);
    assertThat(clarifications.get(owner, created.requestId()).status())
        .isEqualTo(ClarificationRequest.Status.PENDING);
  }

  @Test
  void anIdenticalDuplicateAnswerIsIdempotent() {
    ClarificationRequest created = clarifications.create(owner, task, draft());

    ClarificationRequest first = clarifications.answer(owner, created.requestId(), answer("a"));
    ClarificationRequest duplicate = clarifications.answer(owner, created.requestId(), answer("a"));

    assertThat(duplicate).isEqualTo(first);
    assertThat(duplicate.status()).isEqualTo(ClarificationRequest.Status.ANSWERED);
    assertThat(store.load()).hasSize(1);
  }

  @Test
  void aConflictingSecondAnswerIsRejected() {
    ClarificationRequest created = clarifications.create(owner, task, draft());
    clarifications.answer(owner, created.requestId(), answer("a"));

    assertThatThrownBy(() -> clarifications.answer(owner, created.requestId(), answer("b")))
        .isInstanceOf(SeaClarifications.AlreadyAnsweredException.class);
    assertThat(clarifications.get(owner, created.requestId()).answers().getFirst().choiceId())
        .isEqualTo("a");
  }

  @Test
  void anAnswerAfterExpiryIsRejectedAndTheRequestIsExpired() {
    ClarificationRequest created =
        clarifications.create(owner, task, draft(), Duration.ofMinutes(5));
    clock.set(NOW.plus(Duration.ofMinutes(6)));

    assertThatThrownBy(() -> clarifications.answer(owner, created.requestId(), answer("a")))
        .isInstanceOfSatisfying(
            SeaClarifications.StaleClarificationException.class,
            exception ->
                assertThat(exception.status()).isEqualTo(ClarificationRequest.Status.EXPIRED));
    assertThat(clarifications.get(owner, created.requestId()).status())
        .isEqualTo(ClarificationRequest.Status.EXPIRED);
  }

  @Test
  void cancelStopsAnsweringAndIsSingleUse() {
    ClarificationRequest created = clarifications.create(owner, task, draft());

    ClarificationRequest cancelled = clarifications.cancel(owner, created.requestId());

    assertThat(cancelled.status()).isEqualTo(ClarificationRequest.Status.CANCELLED);
    assertThatThrownBy(() -> clarifications.cancel(owner, created.requestId()))
        .isInstanceOf(SeaClarifications.StaleClarificationException.class);
    assertThatThrownBy(() -> clarifications.answer(owner, created.requestId(), answer("a")))
        .isInstanceOf(SeaClarifications.StaleClarificationException.class);
  }

  @Test
  void invalidAnswersAreRejectedWithoutChangingState() {
    ClarificationRequest created = clarifications.create(owner, task, draft());

    assertThatThrownBy(() -> clarifications.answer(owner, created.requestId(), List.of()))
        .isInstanceOf(ClarificationRequest.InvalidAnswerException.class);
    assertThatThrownBy(() -> clarifications.answer(owner, created.requestId(), answer("zz")))
        .isInstanceOf(ClarificationRequest.InvalidAnswerException.class);
    assertThat(clarifications.get(owner, created.requestId()).status())
        .isEqualTo(ClarificationRequest.Status.PENDING);
  }

  @Test
  void pendingClarificationsSurviveAStoreRestart() {
    FileSystemClarificationRequestStore filesystem =
        new FileSystemClarificationRequestStore(workspace);
    SeaClarifications first = new SeaClarifications(filesystem, clock::get);
    ClarificationRequest created = first.create(owner, task, draft());
    first.answer(owner, created.requestId(), answer("a"));

    SeaClarifications restarted = new SeaClarifications(filesystem, clock::get);

    ClarificationRequest reloaded = restarted.get(owner, created.requestId());
    assertThat(reloaded.status()).isEqualTo(ClarificationRequest.Status.ANSWERED);
    assertThat(reloaded.answers().getFirst().choiceId()).isEqualTo("a");
    assertThat(restarted.pendingFor(owner, task)).isEmpty();
  }

  @Test
  void aPendingClarificationDeduplicatesTheSameTask() {
    ClarificationRequest first = clarifications.create(owner, task, draft());
    ClarificationRequest second = clarifications.create(owner, task, draft());

    assertThat(second.requestId()).isEqualTo(first.requestId());
    assertThat(store.load()).hasSize(1);
  }

  @Test
  void createRequiresAnOwnerAndAnOwnedTask() {
    assertThatThrownBy(() -> clarifications.create(null, task, draft()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actor");
    assertThatThrownBy(() -> clarifications.create(owner, null, draft()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("task reference");
    assertThatThrownBy(() -> clarifications.create(owner, task, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("draft");
  }

  @Test
  void findToleratesMissingOrUndecodedIdentifiers() {
    assertThat(clarifications.find(owner, null)).isEmpty();
    assertThat(clarifications.find(owner, " ")).isEmpty();
    assertThat(clarifications.find(owner, "missing")).isEmpty();
    assertThatThrownBy(() -> clarifications.get(owner, "missing"))
        .isInstanceOf(SeaClarifications.NotFoundException.class);
  }

  @Test
  void expiredPendingClarificationsAreSweptAndNotListed() {
    ClarificationRequest created =
        clarifications.create(owner, task, draft(), Duration.ofMinutes(5));
    clock.set(NOW.plus(Duration.ofMinutes(10)));

    assertThat(clarifications.pending(owner)).isEmpty();
    assertThat(clarifications.pendingFor(owner, task)).isEmpty();
    assertThat(clarifications.expirePending()).isEmpty();
    assertThat(clarifications.get(owner, created.requestId()).status())
        .isEqualTo(ClarificationRequest.Status.EXPIRED);
  }

  @Test
  void clearRemovesStoredRequests() {
    clarifications.create(owner, task, draft());

    clarifications.clear();

    assertThat(clarifications.recent()).isEmpty();
    assertThat(store.load()).isEmpty();
  }

  private static ClarificationDraft draft() {
    return new ClarificationDraft(
        "Which report?",
        List.of(
            new ClarificationDraft.Question(
                "q1",
                "Which report?",
                List.of(
                    new ClarificationDraft.Choice("a", "Monthly"),
                    new ClarificationDraft.Choice("b", "Weekly")),
                false)));
  }

  private static List<ClarificationRequest.Answer> answer(String choiceId) {
    return List.of(new ClarificationRequest.Answer("q1", choiceId, null));
  }

  private static final class InMemoryStore implements ClarificationStore {
    private final List<ClarificationRequest> values = new ArrayList<>();

    @Override
    public List<ClarificationRequest> load() {
      return List.copyOf(values);
    }

    @Override
    public void save(ClarificationRequest request) {
      values.removeIf(value -> value.requestId().equals(request.requestId()));
      values.add(request);
    }

    @Override
    public void delete(String requestId) {
      values.removeIf(value -> value.requestId().equals(requestId));
    }
  }
}
