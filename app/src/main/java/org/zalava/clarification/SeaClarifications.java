package org.zalava.clarification;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.zalava.accounts.domain.Actor;
import org.zalava.clarification.adapter.out.filesystem.FileSystemClarificationRequestStore;
import org.zalava.clarification.application.port.out.ClarificationStore;
import org.zalava.clarification.domain.ClarificationDraft;
import org.zalava.clarification.domain.ClarificationRequest;
import org.zalava.tasks.domain.ActorTaskReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Actor-owned clarification authority. It persists typed questions/choices, enforces single-use
 * answers, expiry and cancellation, and survives a restart by reloading its filesystem store.
 *
 * <p>Clarification is intentionally not a {@code ToolApproval}: the model may request it, but only
 * the owning actor can answer, and answering resumes the paused job through the existing {@code
 * awaiting_human_input} loop rather than a parallel execution path.
 */
@Component
public class SeaClarifications {

  public static final Duration DEFAULT_TTL = Duration.ofHours(24);
  private static final int MAX_ENTRIES = 50;

  private final ArrayDeque<ClarificationRequest> entries = new ArrayDeque<>();
  private final ClarificationStore store;
  private final Supplier<Instant> clock;

  public SeaClarifications() {
    this(null, Instant::now);
  }

  public SeaClarifications(ClarificationStore store) {
    this(store, Instant::now);
  }

  public SeaClarifications(ClarificationStore store, Supplier<Instant> clock) {
    this.store = store;
    this.clock = clock;
    loadEntries();
  }

  @Autowired
  public SeaClarifications(@Value("${agent.workspace:Unknown}") Resource workspace)
      throws java.io.IOException {
    this(new FileSystemClarificationRequestStore(workspace.getFilePath()));
  }

  public synchronized ClarificationRequest create(
      Actor actor, ActorTaskReference taskReference, ClarificationDraft draft) {
    return create(actor, taskReference, draft, DEFAULT_TTL);
  }

  public synchronized ClarificationRequest create(
      Actor actor, ActorTaskReference taskReference, ClarificationDraft draft, Duration ttl) {
    if (actor == null) throw new IllegalArgumentException("Clarification actor is required");
    if (taskReference == null) {
      throw new IllegalArgumentException("Clarification requires an owned task reference");
    }
    if (draft == null) throw new IllegalArgumentException("Clarification draft is required");
    Instant now = clock.get();
    Optional<ClarificationRequest> existing =
        entries.stream()
            .filter(value -> value.ownedBy(actor.accountId().toString()))
            .filter(value -> taskReference.value().equals(value.taskReference()))
            .filter(value -> value.status() == ClarificationRequest.Status.PENDING)
            .findFirst();
    if (existing.isPresent()) {
      ClarificationRequest pending = existing.get();
      if (!pending.isExpired(now)) return pending;
      replace(pending, pending.expired(now));
    }
    ClarificationRequest request =
        new ClarificationRequest(
            UUID.randomUUID().toString(),
            actor.accountId().toString(),
            taskReference.value(),
            draft.prompt(),
            draft.toQuestions(),
            java.util.Map.of(),
            ClarificationRequest.Status.PENDING,
            now.toString(),
            now.plus(ttl).toString(),
            null,
            null,
            List.of());
    entries.addFirst(request);
    persist(request);
    trim();
    return request;
  }

  public synchronized ClarificationRequest get(Actor actor, String requestId) {
    ClarificationRequest request = find(requestId).orElseThrow(() -> notFound(requestId));
    requireOwner(actor, request);
    return expireIfDue(request, clock.get());
  }

  public synchronized Optional<ClarificationRequest> find(Actor actor, String requestId) {
    return find(requestId).filter(value -> value.ownedBy(actor.accountId().toString()));
  }

  public synchronized List<ClarificationRequest> pending(Actor actor) {
    expedite(clock.get());
    return entries.stream()
        .filter(value -> value.ownedBy(actor.accountId().toString()))
        .filter(value -> value.status() == ClarificationRequest.Status.PENDING)
        .toList();
  }

  public synchronized boolean hasPending(Actor actor, ActorTaskReference taskReference) {
    return !pendingFor(actor, taskReference).isEmpty();
  }

  public synchronized List<ClarificationRequest> pendingFor(
      Actor actor, ActorTaskReference taskReference) {
    expedite(clock.get());
    return entries.stream()
        .filter(value -> value.ownedBy(actor.accountId().toString()))
        .filter(value -> taskReference.value().equals(value.taskReference()))
        .filter(value -> value.status() == ClarificationRequest.Status.PENDING)
        .toList();
  }

  public synchronized List<ClarificationRequest> resolvedFor(
      Actor actor, ActorTaskReference taskReference) {
    expedite(clock.get());
    return entries.stream()
        .filter(value -> value.ownedBy(actor.accountId().toString()))
        .filter(value -> taskReference.value().equals(value.taskReference()))
        .filter(value -> value.status() != ClarificationRequest.Status.PENDING)
        .toList();
  }

  /**
   * Records the owner's answer exactly once. Repeating the identical answer is idempotent; a
   * conflicting or stale answer is rejected.
   */
  public synchronized ClarificationRequest answer(
      Actor actor, String requestId, List<ClarificationRequest.Answer> answers) {
    ClarificationRequest existing = get(actor, requestId);
    if (existing.status() == ClarificationRequest.Status.ANSWERED) {
      if (sameAnswers(existing.answers(), answers)) return existing;
      throw new AlreadyAnsweredException(requestId);
    }
    if (existing.status() != ClarificationRequest.Status.PENDING) {
      throw new StaleClarificationException(requestId, existing.status());
    }
    Instant now = clock.get();
    return replace(existing, existing.answered(answers, now));
  }

  public synchronized ClarificationRequest cancel(Actor actor, String requestId) {
    ClarificationRequest existing = get(actor, requestId);
    if (existing.status() != ClarificationRequest.Status.PENDING) {
      throw new StaleClarificationException(requestId, existing.status());
    }
    return replace(existing, existing.cancelled(clock.get()));
  }

  /** Transitions every due pending clarification to {@code EXPIRED} and returns them. */
  public synchronized List<ClarificationRequest> expirePending() {
    Instant now = clock.get();
    List<ClarificationRequest> expired = new ArrayList<>();
    for (ClarificationRequest request : new ArrayList<>(entries)) {
      if (request.status() == ClarificationRequest.Status.PENDING && request.isExpired(now)) {
        ClarificationRequest updated = replace(request, request.expired(now));
        expired.add(updated);
      }
    }
    return List.copyOf(expired);
  }

  public synchronized List<ClarificationRequest> recent() {
    return List.copyOf(new ArrayList<>(entries));
  }

  public synchronized void clear() {
    if (store != null) entries.forEach(entry -> store.delete(entry.requestId()));
    entries.clear();
  }

  private void expedite(Instant now) {
    for (ClarificationRequest request : new ArrayList<>(entries)) {
      if (request.status() == ClarificationRequest.Status.PENDING && request.isExpired(now)) {
        replace(request, request.expired(now));
      }
    }
  }

  private ClarificationRequest expireIfDue(ClarificationRequest request, Instant now) {
    if (request.status() == ClarificationRequest.Status.PENDING && request.isExpired(now)) {
      return replace(request, request.expired(now));
    }
    return request;
  }

  private Optional<ClarificationRequest> find(String requestId) {
    if (requestId == null || requestId.isBlank()) return Optional.empty();
    return entries.stream().filter(entry -> entry.requestId().equals(requestId)).findFirst();
  }

  private void requireOwner(Actor actor, ClarificationRequest request) {
    if (actor == null || !request.ownedBy(actor.accountId().toString())) {
      throw notFound(request.requestId());
    }
  }

  private ClarificationRequest replace(
      ClarificationRequest existing, ClarificationRequest replacement) {
    entries.remove(existing);
    entries.addFirst(replacement);
    persist(replacement);
    return replacement;
  }

  private void loadEntries() {
    if (store == null) return;
    List<ClarificationRequest> loaded = store.load();
    loaded.stream().limit(MAX_ENTRIES).forEach(entries::addLast);
    loaded.stream().skip(MAX_ENTRIES).forEach(entry -> store.delete(entry.requestId()));
  }

  private void trim() {
    while (entries.size() > MAX_ENTRIES) {
      ClarificationRequest removed = entries.removeLast();
      if (store != null) store.delete(removed.requestId());
    }
  }

  private void persist(ClarificationRequest request) {
    if (store != null) store.save(request);
  }

  private static boolean sameAnswers(
      List<ClarificationRequest.Answer> left, List<ClarificationRequest.Answer> right) {
    List<ClarificationRequest.Answer> safeRight = right == null ? List.of() : right;
    if (left.size() != safeRight.size()) return false;
    return left.containsAll(safeRight) && safeRight.containsAll(left);
  }

  private static NotFoundException notFound(String requestId) {
    return new NotFoundException(requestId);
  }

  public static final class NotFoundException extends RuntimeException {
    public NotFoundException(String requestId) {
      super("SEA clarification request not found: " + requestId);
    }
  }

  public static final class AlreadyAnsweredException extends RuntimeException {
    public AlreadyAnsweredException(String requestId) {
      super("SEA clarification request was already answered: " + requestId);
    }
  }

  public static final class StaleClarificationException extends RuntimeException {
    private final ClarificationRequest.Status status;

    public StaleClarificationException(String requestId, ClarificationRequest.Status status) {
      super("SEA clarification request is no longer pending: " + requestId + " (" + status + ")");
      this.status = status;
    }

    public ClarificationRequest.Status status() {
      return status;
    }
  }
}
