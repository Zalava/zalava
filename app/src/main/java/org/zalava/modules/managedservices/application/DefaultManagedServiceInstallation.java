package org.zalava.modules.managedservices.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.zalava.api.ManagedServiceAuthority;
import org.zalava.api.ZalavaServiceFactoryContext;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceInstallation;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceInstallRequestStore;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceStateStore;

/**
 * Executes aggregate administrator-approved managed-service installs in dependency order through
 * the existing reconciler. Only approved resources are staged; failures stop the run and later
 * services are never started; retries are idempotent over already-completed services.
 */
public final class DefaultManagedServiceInstallation implements ManagedServiceInstallation {

  private static final int PENDING_OVERLAP_SCAN_LIMIT = 100;

  private final ManagedServiceInstallPlanning planning;
  private final ManagedServiceInstallRequestStore requests;
  private final ManagedServiceStateStore states;
  private final ManagedServiceReconciler reconciler;
  private final Clock clock;

  public DefaultManagedServiceInstallation(
      ManagedServiceInstallPlanning planning,
      ManagedServiceInstallRequestStore requests,
      ManagedServiceStateStore states,
      ManagedServiceReconciler reconciler,
      Clock clock) {
    this.planning = Objects.requireNonNull(planning, "planning");
    this.requests = Objects.requireNonNull(requests, "requests");
    this.states = Objects.requireNonNull(states, "states");
    this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public synchronized ManagedServiceInstallRequest plan(PlanRequest command) {
    List<ManagedServiceInstallPlanning.Request> submitted = new ArrayList<>();
    for (ManagedServiceInstallation.PlannedRequest planned : command.requests()) {
      submitted.add(
          new ManagedServiceInstallPlanning.Request(
              authority(planned.moduleId()),
              planned.serviceId(),
              planned.desiredState(),
              planned.grant(),
              planned.dependsOn()));
    }
    ManagedServiceInstallPlanning.Plan plan = planning.plan(submitted);
    rejectPendingOverlap(plan);
    rejectInstalledRevisionMismatch(plan);
    return requests.save(
        new ManagedServiceInstallRequest(
            UUID.randomUUID().toString(),
            clock.instant(),
            plan.services(),
            plan.aggregate(),
            ManagedServiceInstallRequest.Status.PENDING,
            null,
            "Awaiting approval"));
  }

  @Override
  public synchronized ManagedServiceInstallRequest get(String requestId) {
    return load(requestId);
  }

  @Override
  public synchronized List<ManagedServiceInstallRequest> recent(int limit) {
    return requests.recent(limit);
  }

  @Override
  public synchronized ManagedServiceInstallRequest allow(String requestId) {
    ManagedServiceInstallRequest request = load(requestId);
    if (request.status() == ManagedServiceInstallRequest.Status.SUCCEEDED) {
      return request;
    }
    if (request.status() == ManagedServiceInstallRequest.Status.DENIED) {
      throw new ManagedServiceInstallException(
          "Managed-service install request " + requestId + " was denied");
    }
    return execute(request);
  }

  @Override
  public synchronized ManagedServiceInstallRequest deny(String requestId) {
    ManagedServiceInstallRequest request = load(requestId);
    if (request.status() == ManagedServiceInstallRequest.Status.DENIED) {
      return request;
    }
    if (request.status() != ManagedServiceInstallRequest.Status.PENDING) {
      throw new ManagedServiceInstallException(
          "Managed-service install request "
              + requestId
              + " is "
              + request.status()
              + " and can no longer be denied");
    }
    return save(
        request,
        ManagedServiceInstallRequest.Status.DENIED,
        "Install denied; no service was started");
  }

  private ManagedServiceInstallRequest execute(ManagedServiceInstallRequest request) {
    ManagedServiceInstallRequest executing =
        save(request, ManagedServiceInstallRequest.Status.EXECUTING, "Executing approved install");
    int successes = 0;
    for (ManagedServiceInstallPlanning.PlannedService planned : executing.services()) {
      ManagedServiceRecord existing = states.find(planned.serviceId()).orElse(null);
      if (existing != null && atTarget(existing)) {
        successes++;
        continue;
      }
      ManagedServiceRecord record =
          existing == null ? initialRecord(planned) : requireMatchingRecord(planned, existing);
      ManagedServiceRecord reconciled = reconciler.reconcile(authority(planned.moduleId()), record);
      ManagedServiceRecord current = states.find(planned.serviceId()).orElse(reconciled);
      if (!atTarget(current)) {
        return save(
            executing,
            successes == 0
                ? ManagedServiceInstallRequest.Status.FAILED
                : ManagedServiceInstallRequest.Status.PARTIALLY_FAILED,
            "Stopped at '"
                + planned.serviceId()
                + "' in observed state "
                + current.observedState()
                + "; later services were not started; retry honors reconciler backoff");
      }
      successes++;
    }
    return save(
        executing,
        ManagedServiceInstallRequest.Status.SUCCEEDED,
        "All planned services reached their desired state");
  }

  private ManagedServiceRecord initialRecord(ManagedServiceInstallPlanning.PlannedService planned) {
    return new ManagedServiceRecord(
        planned.serviceId(),
        planned.desiredState(),
        planned.desiredRevision(),
        planned.grant(),
        planned.grantRevision(),
        ManagedServiceObservedState.ABSENT,
        null,
        "managed-" + planned.serviceId(),
        0,
        null);
  }

  private ManagedServiceRecord requireMatchingRecord(
      ManagedServiceInstallPlanning.PlannedService planned, ManagedServiceRecord existing) {
    if (!existing.desiredState().equals(planned.desiredState())
        || !existing.grant().equals(planned.grant())) {
      throw new ManagedServiceInstallException(
          "Managed service '"
              + planned.serviceId()
              + "' is already installed with a different revision or grant; "
              + "changes are upgrade work outside this install workflow");
    }
    return existing;
  }

  private static boolean atTarget(ManagedServiceRecord record) {
    return switch (record.desiredState().lifecycle()) {
      case RUNNING -> record.observedState() == ManagedServiceObservedState.RUNNING;
      case STOPPED -> record.observedState() == ManagedServiceObservedState.STOPPED;
    };
  }

  private static ManagedServiceAuthority authority(String moduleId) {
    return new ZalavaServiceFactoryContext(moduleId, Map.of(), Map.of()).managedServiceAuthority();
  }

  private void rejectPendingOverlap(ManagedServiceInstallPlanning.Plan plan) {
    Set<String> requested = new HashSet<>();
    plan.services().forEach(planned -> requested.add(planned.serviceId()));
    for (ManagedServiceInstallRequest pending : requests.recent(PENDING_OVERLAP_SCAN_LIMIT)) {
      if (pending.status() != ManagedServiceInstallRequest.Status.PENDING
          && pending.status() != ManagedServiceInstallRequest.Status.EXECUTING) {
        continue;
      }
      for (ManagedServiceInstallPlanning.PlannedService planned : pending.services()) {
        if (requested.contains(planned.serviceId())) {
          throw new ManagedServiceInstallException(
              "Managed service '"
                  + planned.serviceId()
                  + "' already has a pending install request "
                  + pending.requestId()
                  + "; resolve it before planning again");
        }
      }
    }
  }

  private void rejectInstalledRevisionMismatch(ManagedServiceInstallPlanning.Plan plan) {
    for (ManagedServiceInstallPlanning.PlannedService planned : plan.services()) {
      states
          .find(planned.serviceId())
          .ifPresent(
              existing -> {
                if (!existing.desiredState().equals(planned.desiredState())
                    || !existing.grant().equals(planned.grant())) {
                  throw new ManagedServiceInstallException(
                      "Managed service '"
                          + planned.serviceId()
                          + "' is already installed with a different revision or grant; "
                          + "changes are upgrade work outside this install workflow");
                }
              });
    }
  }

  private ManagedServiceInstallRequest load(String requestId) {
    return requests
        .find(requestId)
        .orElseThrow(
            () -> new ManagedServiceInstallException("Unknown install request: " + requestId));
  }

  private ManagedServiceInstallRequest save(
      ManagedServiceInstallRequest request,
      ManagedServiceInstallRequest.Status status,
      String message) {
    Instant decidedAt =
        status == ManagedServiceInstallRequest.Status.PENDING
                || status == ManagedServiceInstallRequest.Status.EXECUTING
            ? null
            : clock.instant();
    return requests.save(
        new ManagedServiceInstallRequest(
            request.requestId(),
            request.createdAt(),
            request.services(),
            request.aggregate(),
            status,
            decidedAt,
            message));
  }
}
