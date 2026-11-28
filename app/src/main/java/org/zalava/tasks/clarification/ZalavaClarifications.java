package org.zalava.tasks.clarification;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.clarification.adapter.out.filesystem.FileSystemClarificationRequestStore;
import org.zalava.tasks.clarification.application.port.out.ClarificationStore;
import org.zalava.tasks.clarification.domain.ClarificationDraft;
import org.zalava.tasks.clarification.domain.ClarificationRequest;
import org.zalava.tasks.domain.ActorTaskReference;

/**
 * Actor-owned clarification authority. It persists typed questions/choices, enforces single-use
 * answers, expiry and cancellation, and survives a restart by reloading its filesystem store.
 *
 * <p>Clarification is intentionally not a {@code ToolApproval}: the model may request it, but only
 * the owning actor can answer, and answering resumes the paused job through the existing {@code
 * awaiting_human_input} loop rather than a parallel execution path.
 */
@Component
public class ZalavaClarifications {

  public static final Duration DEFAULT_TTL = Duration.ofHours(24);
  private static final int MAX_ENTRIES = 50;

  private final ArrayDeque<ClarificationRequest> entries = new ArrayDeque<>();
  private final ClarificationStore store;
  private final Supplier<Instant> clock;

  public ZalavaClarifications() {
    this(null, Instant::now);
  }

  public ZalavaClarifications(ClarificationStore store) {
    this(store, Instant::now);
  }

  public ZalavaClarifications(ClarificationStore store, Supplier<Instant> clock) {
    this.store = store;
    this.clock = clock;
    loadEntries();
  }

  @Autowired
  public ZalavaClarifications(@Value("${agent.workspace:Unknown}") Resource workspace)
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
      if (expireIfDue(pending, now) == pending) return pending;
    }
    ClarificationRequest request =
        new ClarificationRequest(
            UUID.randomUUID().toString(),
            actor.accountId().toString(),
            taskReference.value(),
            draft.prompt(),
            draft.toQuestions(),
            Map.of(),
            ClarificationRequest.Status.PENDING,
            now.toString(),
            now.plus(ttl).toString(),
            null,
            null,
            List.of());
    persist(request);
    entries.addFirst(request);
    trim();
    return request;
  }

  public synchronized ClarificationRequest get(Actor actor, String requestId) {
    ClarificationRequest request = ownedRequest(actor, requestId);
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
    ClarificationRequest request = ownedRequest(actor, requestId);
    Instant now = clock.get();
    ClarificationRequest existing = expireIfDue(request, now);
    return replace(existing, existing.answered(answers, now));
  }

  public synchronized ClarificationRequest cancel(Actor actor, String requestId) {
    ClarificationRequest request = ownedRequest(actor, requestId);
    Instant now = clock.get();
    ClarificationRequest existing = expireIfDue(request, now);
    return replace(existing, existing.cancelled(now));
  }

  /** Transitions every due pending clarification to {@code EXPIRED} and returns them. */
  public synchronized List<ClarificationRequest> expirePending() {
    Instant now = clock.get();
    List<ClarificationRequest> expired = new ArrayList<>();
    for (ClarificationRequest request : new ArrayList<>(entries)) {
      ClarificationRequest updated = expireIfDue(request, now);
      if (updated != request) {
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
      expireIfDue(request, now);
    }
  }

  private ClarificationRequest expireIfDue(ClarificationRequest request, Instant now) {
    return replace(request, request.expireIfDue(now));
  }

  private Optional<ClarificationRequest> find(String requestId) {
    if (requestId == null || requestId.isBlank()) return Optional.empty();
    return entries.stream().filter(entry -> entry.requestId().equals(requestId)).findFirst();
  }

  private ClarificationRequest ownedRequest(Actor actor, String requestId) {
    ClarificationRequest request = find(requestId).orElseThrow(() -> notFound(requestId));
    if (actor == null || !request.ownedBy(actor.accountId().toString())) {
      throw notFound(request.requestId());
    }
    return request;
  }

  private ClarificationRequest replace(
      ClarificationRequest existing, ClarificationRequest replacement) {
    if (replacement == existing) return existing;
    persist(replacement);
    entries.remove(existing);
    entries.addFirst(replacement);
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

  private static NotFoundException notFound(String requestId) {
    return new NotFoundException(requestId);
  }

  public static final class NotFoundException extends RuntimeException {
    public NotFoundException(String requestId) {
      super("Zalava clarification request not found: " + requestId);
    }
  }
}
