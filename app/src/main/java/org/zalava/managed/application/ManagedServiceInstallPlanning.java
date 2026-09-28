package org.zalava.managed.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.zalava.ManagedServiceAuthority;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycleResult;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;
import org.zalava.managed.ManagedServiceValidator;

/**
 * Validates a submitted set of managed services and derives the exact aggregate resources an
 * administrator approves. Pure and deterministic: no engine, store, or clock access.
 */
public final class ManagedServiceInstallPlanning {

  private final Supplier<String> grantRevisionGenerator;

  public ManagedServiceInstallPlanning() {
    this(() -> "grant-" + java.util.UUID.randomUUID());
  }

  public ManagedServiceInstallPlanning(Supplier<String> grantRevisionGenerator) {
    this.grantRevisionGenerator =
        Objects.requireNonNull(grantRevisionGenerator, "grantRevisionGenerator");
  }

  /** Plans the submitted requests into dependency order with aggregated resources. */
  public Plan plan(List<Request> requests) {
    Objects.requireNonNull(requests, "requests");
    if (requests.isEmpty()) {
      throw new ManagedServiceInstallPlanningException(
          "Managed-service install plan must contain at least one service");
    }
    Map<String, Request> byServiceId = new LinkedHashMap<>();
    for (Request request : requests) {
      Objects.requireNonNull(request, "request");
      request.validate();
      if (byServiceId.putIfAbsent(request.serviceId(), request) != null) {
        throw new ManagedServiceInstallPlanningException(
            "Duplicate managed service id in install plan: " + request.serviceId());
      }
    }
    for (Request request : byServiceId.values()) {
      for (String dependency : request.dependsOn()) {
        if (dependency.equals(request.serviceId())) {
          throw new ManagedServiceInstallPlanningException(
              "Managed service must not depend on itself: " + request.serviceId());
        }
        if (!byServiceId.containsKey(dependency)) {
          throw new ManagedServiceInstallPlanningException(
              "Unknown managed-service dependency '"
                  + dependency
                  + "' requested by '"
                  + request.serviceId()
                  + "'");
        }
      }
    }
    return new Plan(order(byServiceId), aggregate(byServiceId.values()));
  }

  /** Kahn's algorithm with lexicographic tie-breaking for deterministic ordering. */
  private List<PlannedService> order(Map<String, Request> byServiceId) {
    Map<String, Integer> remainingDependencies = new HashMap<>();
    Map<String, List<String>> dependents = new HashMap<>();
    for (Request request : byServiceId.values()) {
      remainingDependencies.put(request.serviceId(), request.dependsOn().size());
      for (String dependency : request.dependsOn()) {
        dependents
            .computeIfAbsent(dependency, ignored -> new ArrayList<>())
            .add(request.serviceId());
      }
    }
    PriorityQueue<String> ready = new PriorityQueue<>();
    for (Map.Entry<String, Integer> entry : remainingDependencies.entrySet()) {
      if (entry.getValue() == 0) {
        ready.add(entry.getKey());
      }
    }
    List<PlannedService> ordered = new ArrayList<>(byServiceId.size());
    Set<String> planned = new HashSet<>();
    while (!ready.isEmpty()) {
      String serviceId = ready.poll();
      planned.add(serviceId);
      Request request = byServiceId.get(serviceId);
      Set<String> satisfied = new TreeSet<>(request.dependsOn());
      if (!planned.containsAll(satisfied)) {
        throw new ManagedServiceInstallPlanningException(
            "Managed-service install plan contains a dependency cycle involving: "
                + new TreeSet<>(byServiceId.keySet()));
      }
      ordered.add(request.plan(grantRevisionGenerator.get()));
      for (String dependent : dependents.getOrDefault(serviceId, List.of())) {
        int remaining = remainingDependencies.merge(dependent, -1, Integer::sum);
        if (remaining == 0) {
          ready.add(dependent);
        }
      }
    }
    if (ordered.size() != byServiceId.size()) {
      throw new ManagedServiceInstallPlanningException(
          "Managed-service install plan contains a dependency cycle involving: "
              + new TreeSet<>(byServiceId.keySet()));
    }
    return List.copyOf(ordered);
  }

  private AggregatedResources aggregate(Iterable<Request> requests) {
    Set<Integer> ports = new TreeSet<>();
    Set<String> secretReferences = new TreeSet<>();
    Set<String> dataPaths = new TreeSet<>();
    Set<String> devices = new TreeSet<>();
    long totalCpuMillis = 0;
    long totalMemoryBytes = 0;
    int totalProcessLimit = 0;
    for (Request request : requests) {
      ports.addAll(request.desiredState().ports());
      secretReferences.addAll(request.desiredState().secretReferences());
      dataPaths.addAll(request.desiredState().dataPaths());
      devices.addAll(request.desiredState().devices());
      ManagedServiceLimits limits = request.desiredState().limits();
      totalCpuMillis = Math.addExact(totalCpuMillis, limits.cpuMillis());
      totalMemoryBytes = Math.addExact(totalMemoryBytes, limits.memoryBytes());
      totalProcessLimit = Math.addExact(totalProcessLimit, limits.processLimit());
    }
    return new AggregatedResources(
        Set.copyOf(ports),
        Set.copyOf(secretReferences),
        Set.copyOf(dataPaths),
        Set.copyOf(devices),
        new ManagedServiceLimits(totalCpuMillis, totalMemoryBytes, totalProcessLimit));
  }

  /** One submitted managed-service request; ownership is bound before planning. */
  public record Request(
      ManagedServiceAuthority authority,
      String serviceId,
      ManagedServiceDesiredState desiredState,
      ManagedServiceResourceGrant grant,
      Set<String> dependsOn) {

    public Request {
      Objects.requireNonNull(authority, "authority");
      Objects.requireNonNull(serviceId, "serviceId");
      if (!serviceId.matches("[a-z][a-z0-9-]{0,62}")) {
        throw new ManagedServiceInstallPlanningException(
            "Managed service id must be canonical: " + serviceId);
      }
      Objects.requireNonNull(desiredState, "desiredState");
      Objects.requireNonNull(grant, "grant");
      dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
    }

    void validate() {
      if (!authority.moduleId().equals(grant.moduleId())) {
        throw new ManagedServiceInstallPlanningException(
            "Managed-service grant for '"
                + serviceId
                + "' belongs to module '"
                + grant.moduleId()
                + "', not the requesting module '"
                + authority.moduleId()
                + "'");
      }
      if (ManagedServiceValidator.validate(authority, desiredState, grant)
          instanceof ManagedServiceLifecycleResult.Rejected rejected) {
        throw new ManagedServiceInstallPlanningException(
            "Managed-service desired state for '"
                + serviceId
                + "' exceeds its grant: "
                + rejected.failures());
      }
    }

    PlannedService plan(String grantRevision) {
      return new PlannedService(
          authority.moduleId(),
          serviceId,
          desiredState,
          grant,
          desiredState.revision(),
          grantRevision,
          dependsOn);
    }
  }

  /** One planned service in dependency order with the exact approved revisions. */
  public record PlannedService(
      String moduleId,
      String serviceId,
      ManagedServiceDesiredState desiredState,
      ManagedServiceResourceGrant grant,
      String desiredRevision,
      String grantRevision,
      Set<String> dependsOn) {

    public PlannedService {
      Objects.requireNonNull(moduleId, "moduleId");
      Objects.requireNonNull(serviceId, "serviceId");
      Objects.requireNonNull(desiredState, "desiredState");
      Objects.requireNonNull(grant, "grant");
      Objects.requireNonNull(desiredRevision, "desiredRevision");
      Objects.requireNonNull(grantRevision, "grantRevision");
      dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
    }
  }

  /** The exact union of resources an administrator approves in one aggregate decision. */
  public record AggregatedResources(
      Set<Integer> ports,
      Set<String> secretReferences,
      Set<String> dataPaths,
      Set<String> devices,
      ManagedServiceLimits totalLimits) {

    public AggregatedResources {
      ports = ports == null ? Set.of() : Set.copyOf(ports);
      secretReferences = secretReferences == null ? Set.of() : Set.copyOf(secretReferences);
      dataPaths = dataPaths == null ? Set.of() : Set.copyOf(dataPaths);
      devices = devices == null ? Set.of() : Set.copyOf(devices);
      Objects.requireNonNull(totalLimits, "totalLimits");
    }
  }

  /** Validated, ordered plan ready for aggregate administrator approval. */
  public record Plan(List<PlannedService> services, AggregatedResources aggregate) {

    public Plan {
      services = services == null ? List.of() : List.copyOf(services);
      Objects.requireNonNull(aggregate, "aggregate");
    }
  }
}
