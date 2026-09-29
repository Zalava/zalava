package org.zalava.managed.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.zalava.ManagedServiceAuthority;
import org.zalava.ZalavaServiceFactoryContext;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLifecycleResult;
import org.zalava.managed.ManagedServiceValidator;
import org.zalava.managed.application.ManagedServiceUpgradeRequest.Phase;
import org.zalava.managed.application.port.in.ManagedServiceUpgrade;
import org.zalava.managed.application.port.out.ManagedServiceBackupPort;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;
import org.zalava.managed.application.port.out.ManagedServiceUpgradeRequestStore;
import org.zalava.managed.application.port.out.OciServiceEngine;

/**
 * Executes administrator-approved upgrade sets: quiesce, back up declared data, remove the owned
 * engine resource, and promote the candidate through the existing reconciler. When a candidate
 * fails readiness or the engine rejects it, the captured previous revision is promoted back
 * automatically and its backup restored. Per-service progress is persisted around every engine
 * phase, so an interrupted upgrade resumes or rolls back on restart instead of losing state.
 */
public final class DefaultManagedServiceUpgrade implements ManagedServiceUpgrade {

  private static final int RECOVERY_SCAN_LIMIT = 100;

  private final ManagedServiceUpgradeRequestStore requests;
  private final ManagedServiceStateStore states;
  private final ManagedServiceReconciler reconciler;
  private final OciServiceEngine engine;
  private final ManagedServiceBackupPort backups;
  private final Clock clock;

  public DefaultManagedServiceUpgrade(
      ManagedServiceUpgradeRequestStore requests,
      ManagedServiceStateStore states,
      ManagedServiceReconciler reconciler,
      OciServiceEngine engine,
      ManagedServiceBackupPort backups,
      Clock clock) {
    this.requests = Objects.requireNonNull(requests, "requests");
    this.states = Objects.requireNonNull(states, "states");
    this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.backups = Objects.requireNonNull(backups, "backups");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public synchronized ManagedServiceUpgradeRequest plan(UpgradePlanRequest request) {
    if (request.candidates().isEmpty()) {
      throw new ManagedServiceUpgradeValidationException(
          "Managed-service upgrade plan must contain at least one service");
    }
    List<ManagedServiceUpgradeRequest.PlannedUpgrade> planned = new ArrayList<>();
    Set<String> seen = new java.util.HashSet<>();
    for (UpgradeCandidate candidate : request.candidates()) {
      if (!seen.add(candidate.serviceId())) {
        throw new ManagedServiceUpgradeValidationException(
            "Duplicate managed service id in upgrade plan: " + candidate.serviceId());
      }
      planned.add(validateCandidate(candidate));
    }
    return requests.save(
        new ManagedServiceUpgradeRequest(
            UUID.randomUUID().toString(),
            1L,
            clock.instant(),
            planned,
            ManagedServiceUpgradeRequest.Status.PENDING,
            null,
            "Awaiting approval"));
  }

  @Override
  public synchronized ManagedServiceUpgradeRequest get(String requestId) {
    return load(requestId);
  }

  @Override
  public synchronized List<ManagedServiceUpgradeRequest> recent(int limit) {
    return requests.recent(limit);
  }

  @Override
  public synchronized ManagedServiceUpgradeRequest allow(
      String requestId, long expectedGeneration) {
    ManagedServiceUpgradeRequest request = load(requestId);
    requireCurrentGeneration(request, expectedGeneration);
    if (request.status() == ManagedServiceUpgradeRequest.Status.SUCCEEDED
        || request.status() == ManagedServiceUpgradeRequest.Status.ROLLED_BACK) {
      return request;
    }
    if (request.status() == ManagedServiceUpgradeRequest.Status.DENIED) {
      throw new ManagedServiceUpgradeException(
          "Managed-service upgrade request " + requestId + " was denied");
    }
    if (request.status() == ManagedServiceUpgradeRequest.Status.PROMOTING) {
      throw new ManagedServiceUpgradeException(
          "Managed-service upgrade request "
              + requestId
              + " is already executing; restart recovery owns its continuation");
    }
    if (request.status() == ManagedServiceUpgradeRequest.Status.FAILED) {
      throw new ManagedServiceUpgradeException(
          "Managed-service upgrade request " + requestId + " failed; plan a new upgrade");
    }
    return execute(request);
  }

  @Override
  public synchronized ManagedServiceUpgradeRequest deny(String requestId, long expectedGeneration) {
    ManagedServiceUpgradeRequest request = load(requestId);
    requireCurrentGeneration(request, expectedGeneration);
    if (request.status() == ManagedServiceUpgradeRequest.Status.DENIED) {
      return request;
    }
    if (request.status() != ManagedServiceUpgradeRequest.Status.PENDING) {
      throw new ManagedServiceUpgradeException(
          "Managed-service upgrade request "
              + requestId
              + " is "
              + request.status()
              + " and can no longer be denied");
    }
    return requests.save(
        request.withStatus(
            ManagedServiceUpgradeRequest.Status.DENIED,
            "Upgrade denied; no service was touched",
            clock.instant()));
  }

  @Override
  public synchronized void recover() {
    for (ManagedServiceUpgradeRequest request : requests.recent(RECOVERY_SCAN_LIMIT)) {
      if (request.status() == ManagedServiceUpgradeRequest.Status.PROMOTING) {
        runPromotion(request, new ArrayList<>(request.services()), new ArrayList<>());
      }
    }
  }

  private ManagedServiceUpgradeRequest execute(ManagedServiceUpgradeRequest request) {
    return runPromotion(
        requests.save(
            request.withStatus(
                ManagedServiceUpgradeRequest.Status.PROMOTING,
                "Executing approved upgrade",
                clock.instant())),
        new ArrayList<>(request.services()),
        new ArrayList<>());
  }

  /** Drives every service from its persisted phase to a terminal phase, then finalizes. */
  private ManagedServiceUpgradeRequest runPromotion(
      ManagedServiceUpgradeRequest request,
      List<ManagedServiceUpgradeRequest.PlannedUpgrade> services,
      List<String> notes) {
    for (int index = 0; index < services.size(); index++) {
      ManagedServiceUpgradeRequest.PlannedUpgrade planned = services.get(index);
      if (terminal(planned.phase())) {
        continue;
      }
      try {
        services.set(index, promote(request, index, planned));
      } catch (RuntimeException ex) {
        notes.add(planned.serviceId() + ": " + ex.getMessage());
        services.set(index, rollback(request, index, ex));
        if (services.get(index).phase() == Phase.FAILED) {
          // Rollback could not restore this service: never continue promoting later ones.
          break;
        }
      }
    }
    return finalizeExecution(request, services, notes);
  }

  /**
   * Promotes one service from its current phase. Every phase transition is persisted immediately,
   * so a crash between engine operations leaves an exact restart point in the request store.
   */
  private ManagedServiceUpgradeRequest.PlannedUpgrade promote(
      ManagedServiceUpgradeRequest request,
      int index,
      ManagedServiceUpgradeRequest.PlannedUpgrade planned) {
    String serviceId = planned.serviceId();
    ManagedServiceRecord record = loadRecord(serviceId);
    if (planned.phase() == ManagedServiceUpgradeRequest.Phase.PLANNED) {
      record = quiesce(record);
      planned = persistPhase(request, index, planned.withPhase(Phase.STOPPED));
    }
    if (planned.phase() == ManagedServiceUpgradeRequest.Phase.STOPPED) {
      ManagedServiceBackupPort.BackupArtifact artifact = backups.execute(record);
      planned = persistPhase(request, index, planned.backedUp(artifact.location()));
    }
    if (planned.phase() == ManagedServiceUpgradeRequest.Phase.BACKED_UP) {
      removeOwnedResource(serviceId);
      planned = persistPhase(request, index, planned.withPhase(Phase.REMOVED));
    }
    // REMOVED (and recovery re-entry at PROMOTING-per-service): create and start the candidate.
    ManagedServiceRecord promoted =
        reconciler.reconcile(authority(planned.moduleId()), candidateRecord(record, planned));
    ManagedServiceRecord current = states.find(serviceId).orElse(promoted);
    if (!atTarget(current)) {
      throw new ManagedServiceUpgradeException(
          "Candidate revision '"
              + planned.candidateState().revision()
              + "' did not reach its desired state for '"
              + serviceId
              + "' (observed "
              + current.observedState()
              + "); automatic rollback follows");
    }
    return persistPhase(request, index, planned.withPhase(Phase.PROMOTED));
  }

  /**
   * Puts the captured previous revision back. The phase reached is read from the persisted request
   * so rollback knows exactly which engine operations already happened: before the backup succeeded
   * the previous container is still intact (no restore, no removal), after it the owned resource is
   * restored from the backup, removed, and the previous revision re-created.
   */
  private ManagedServiceUpgradeRequest.PlannedUpgrade rollback(
      ManagedServiceUpgradeRequest request, int index, RuntimeException cause) {
    ManagedServiceUpgradeRequest persisted = requests.find(request.requestId()).orElse(request);
    ManagedServiceUpgradeRequest.PlannedUpgrade planned = persisted.services().get(index);
    String serviceId = planned.serviceId();
    try {
      ManagedServiceRecord record = loadRecord(serviceId);
      record = quiesce(record);
      boolean backedUp = planned.backupLocation() != null && !planned.backupLocation().isBlank();
      if (backedUp) {
        backups.restore(record, planned.backupLocation());
        removeOwnedResource(serviceId);
      }
      ManagedServiceRecord restored =
          reconciler.reconcile(authority(planned.moduleId()), previousRecord(record, planned));
      ManagedServiceRecord current = states.find(serviceId).orElse(restored);
      if (!atTarget(current)) {
        throw new ManagedServiceUpgradeException(
            "Rollback of '"
                + serviceId
                + "' did not reach its desired state (observed "
                + current.observedState()
                + ")");
      }
      return persistPhase(request, index, planned.withPhase(Phase.ROLLED_BACK));
    } catch (RuntimeException rollbackFailure) {
      // A failed rollback is recorded honestly as FAILED; the exception never escapes so the
      // request always reaches a terminal status instead of hanging in PROMOTING.
      rollbackFailure.addSuppressed(cause);
      return persistPhase(request, index, planned.withPhase(Phase.FAILED));
    }
  }

  private ManagedServiceUpgradeRequest finalizeExecution(
      ManagedServiceUpgradeRequest request,
      List<ManagedServiceUpgradeRequest.PlannedUpgrade> services,
      List<String> notes) {
    boolean anyFailed = services.stream().anyMatch(s -> s.phase() == Phase.FAILED);
    boolean anyRolledBack = services.stream().anyMatch(s -> s.phase() == Phase.ROLLED_BACK);
    String note = String.join("; ", notes);
    if (anyFailed) {
      return requests.save(
          request
              .withServices(services)
              .withStatus(
                  ManagedServiceUpgradeRequest.Status.FAILED,
                  "Upgrade failed; rollback could not restore the previous revision and later "
                      + "services were not upgraded"
                      + (note.isBlank() ? "" : ": " + note),
                  clock.instant()));
    }
    if (anyRolledBack) {
      return requests.save(
          request
              .withServices(services)
              .withStatus(
                  ManagedServiceUpgradeRequest.Status.ROLLED_BACK,
                  "Rolled back to the previous revisions; later services were not upgraded"
                      + (note.isBlank() ? "" : ": " + note),
                  clock.instant()));
    }
    return requests.save(
        request
            .withServices(services)
            .withStatus(
                ManagedServiceUpgradeRequest.Status.SUCCEEDED,
                "All upgraded services reached their candidate revisions",
                clock.instant()));
  }

  /**
   * Reconciles the recorded service to stopped. The stop target keeps the record's desired state
   * with only the lifecycle flipped, so the reconciler still validates against the same grant.
   */
  private ManagedServiceRecord quiesce(ManagedServiceRecord record) {
    if (record.observedState() == ManagedServiceObservedState.STOPPED) {
      return record;
    }
    ManagedServiceDesiredState previous = record.desiredState();
    ManagedServiceDesiredState stopped =
        new ManagedServiceDesiredState(
            previous.resourceId(),
            previous.artifactReference(),
            previous.revision(),
            ManagedServiceLifecycle.STOPPED,
            previous.secretReferences(),
            previous.dataPaths(),
            previous.ports(),
            previous.devices(),
            previous.limits(),
            previous.readinessDeadline(),
            previous.restartLimit());
    ManagedServiceRecord target =
        new ManagedServiceRecord(
            record.serviceId(),
            stopped,
            record.desiredRevision(),
            record.grant(),
            record.grantRevision(),
            record.observedState(),
            record.observedRevision(),
            record.ownedDataIdentity(),
            0,
            null);
    ManagedServiceRecord reconciled =
        reconciler.reconcile(authority(record.grant().moduleId()), target);
    ManagedServiceRecord current = states.find(record.serviceId()).orElse(reconciled);
    if (current.observedState() != ManagedServiceObservedState.STOPPED) {
      throw new ManagedServiceUpgradeException(
          "Could not stop '"
              + record.serviceId()
              + "' before promotion (observed "
              + current.observedState()
              + ")");
    }
    return current;
  }

  /** Removes the owned engine resource unless the engine already reports it absent. */
  private void removeOwnedResource(String serviceId) {
    OciServiceEngine.Observation observed = engine.inspect(serviceId);
    if (observed.exists()) {
      engine.remove(serviceId);
    }
  }

  /** Candidate reconciliation record: fresh failure counters, same data identity and grant. */
  private ManagedServiceRecord candidateRecord(
      ManagedServiceRecord record, ManagedServiceUpgradeRequest.PlannedUpgrade planned) {
    return new ManagedServiceRecord(
        record.serviceId(),
        planned.candidateState(),
        planned.candidateState().revision(),
        record.grant(),
        record.grantRevision(),
        ManagedServiceObservedState.ABSENT,
        null,
        record.ownedDataIdentity(),
        0,
        null);
  }

  /** Rollback reconciliation record: the captured previous state with fresh failure counters. */
  private ManagedServiceRecord previousRecord(
      ManagedServiceRecord record, ManagedServiceUpgradeRequest.PlannedUpgrade planned) {
    return new ManagedServiceRecord(
        record.serviceId(),
        planned.previousState(),
        planned.previousState().revision(),
        record.grant(),
        record.grantRevision(),
        ManagedServiceObservedState.ABSENT,
        null,
        planned.previousDataIdentity(),
        0,
        null);
  }

  private static boolean atTarget(ManagedServiceRecord record) {
    return switch (record.desiredState().lifecycle()) {
      case RUNNING -> record.observedState() == ManagedServiceObservedState.RUNNING;
      case STOPPED -> record.observedState() == ManagedServiceObservedState.STOPPED;
    };
  }

  private static boolean terminal(ManagedServiceUpgradeRequest.Phase phase) {
    return phase == Phase.PROMOTED || phase == Phase.ROLLED_BACK || phase == Phase.FAILED;
  }

  private ManagedServiceUpgradeRequest.PlannedUpgrade validateCandidate(
      UpgradeCandidate candidate) {
    ManagedServiceRecord record =
        states
            .find(candidate.serviceId())
            .orElseThrow(
                () ->
                    new ManagedServiceUpgradeValidationException(
                        "Unknown managed service: " + candidate.serviceId()));
    if (!record.grant().moduleId().equals(candidate.moduleId())) {
      throw new ManagedServiceUpgradeValidationException(
          "Managed service '"
              + candidate.serviceId()
              + "' belongs to module '"
              + record.grant().moduleId()
              + "', not the requesting module '"
              + candidate.moduleId()
              + "'");
    }
    if (!record.desiredState().resourceId().equals(candidate.candidateState().resourceId())) {
      throw new ManagedServiceUpgradeValidationException(
          "Upgrade of '"
              + candidate.serviceId()
              + "' must keep its resource identity '"
              + record.desiredState().resourceId()
              + "'");
    }
    if (!candidate.candidateState().dataPaths().equals(record.desiredState().dataPaths())) {
      throw new ManagedServiceUpgradeValidationException(
          "Upgrade of '"
              + candidate.serviceId()
              + "' must keep its declared data paths; resource-surface changes are install work");
    }
    if (!candidate.candidateState().ports().equals(record.desiredState().ports())) {
      throw new ManagedServiceUpgradeValidationException(
          "Upgrade of '"
              + candidate.serviceId()
              + "' must keep its declared ports; resource-surface changes are install work");
    }
    if (candidate.candidateState().revision().equals(record.desiredState().revision())) {
      throw new ManagedServiceUpgradeValidationException(
          "Managed service '"
              + candidate.serviceId()
              + "' is already at revision '"
              + record.desiredState().revision()
              + "'");
    }
    if (ManagedServiceValidator.validate(
            authority(candidate.moduleId()), candidate.candidateState(), record.grant())
        instanceof ManagedServiceLifecycleResult.Rejected rejected) {
      throw new ManagedServiceUpgradeValidationException(
          "Candidate state for '"
              + candidate.serviceId()
              + "' exceeds its grant: "
              + rejected.failures());
    }
    return new ManagedServiceUpgradeRequest.PlannedUpgrade(
        candidate.moduleId(),
        candidate.serviceId(),
        candidate.candidateState(),
        record.desiredState(),
        record.ownedDataIdentity(),
        ManagedServiceUpgradeRequest.Phase.PLANNED,
        null);
  }

  /** Persists one service's new phase immediately; a crash before this leaves the old phase. */
  private ManagedServiceUpgradeRequest.PlannedUpgrade persistPhase(
      ManagedServiceUpgradeRequest request,
      int index,
      ManagedServiceUpgradeRequest.PlannedUpgrade updated) {
    List<ManagedServiceUpgradeRequest.PlannedUpgrade> services =
        new ArrayList<>(request.services());
    services.set(index, updated);
    ManagedServiceUpgradeRequest persisted =
        requests.save(
            new ManagedServiceUpgradeRequest(
                request.requestId(),
                request.generation(),
                request.createdAt(),
                services,
                request.status(),
                request.decidedAt(),
                request.message()));
    return persisted.services().get(index);
  }

  private ManagedServiceRecord loadRecord(String serviceId) {
    return states
        .find(serviceId)
        .orElseThrow(
            () ->
                new ManagedServiceUpgradeException(
                    "Managed service vanished during upgrade: " + serviceId));
  }

  private ManagedServiceUpgradeRequest load(String requestId) {
    return requests
        .find(requestId)
        .orElseThrow(() -> new UnknownUpgradeRequestException(requestId));
  }

  private void requireCurrentGeneration(
      ManagedServiceUpgradeRequest request, long expectedGeneration) {
    if (request.generation() != expectedGeneration) {
      throw new ManagedServiceUpgradeException(
          "Stale upgrade operation on "
              + request.requestId()
              + ": expected generation "
              + expectedGeneration
              + " but current generation is "
              + request.generation()
              + "; reload the request and retry");
    }
  }

  private static ManagedServiceAuthority authority(String moduleId) {
    return new ZalavaServiceFactoryContext(moduleId, Map.of(), Map.of()).managedServiceAuthority();
  }
}
