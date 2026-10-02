package org.zalava.modules.managedservices.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.zalava.api.extensions.managed.ManagedServiceDeclaration;
import org.zalava.api.extensions.managed.ManagedServiceDesiredState;
import org.zalava.api.extensions.managed.ManagedServiceResourceGrant;
import org.zalava.modules.managedservices.application.port.in.DeclaredManagedServices;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceInstallation;
import org.zalava.modules.managedservices.application.port.in.ModuleManagedServiceInstallation;

/** Derives the aggregate install request for a module's declared managed services. */
public final class DefaultModuleManagedServiceInstallation
    implements ModuleManagedServiceInstallation {

  private static final int RECENT_REQUEST_LIMIT = 50;

  private final DeclaredManagedServices declarations;
  private final ManagedServiceInstallation installations;

  public DefaultModuleManagedServiceInstallation(
      DeclaredManagedServices declarations, ManagedServiceInstallation installations) {
    this.declarations = Objects.requireNonNull(declarations, "declarations");
    this.installations = Objects.requireNonNull(installations, "installations");
  }

  @Override
  public ManagedServiceInstallRequest requestDeclaredInstall(String moduleId) {
    List<ManagedServiceDeclaration> declared = declarations.forModule(moduleId);
    if (declared.isEmpty()) {
      throw new IllegalArgumentException("Module '" + moduleId + "' declares no managed services");
    }
    Map<String, ManagedServiceDeclaration> byServiceId = new LinkedHashMap<>();
    for (ManagedServiceDeclaration declaration : declared) {
      byServiceId.put(declaration.serviceId(), declaration);
    }
    List<ManagedServiceInstallation.PlannedRequest> requests =
        declared.stream()
            .map(
                declaration ->
                    new ManagedServiceInstallation.PlannedRequest(
                        moduleId,
                        declaration.serviceId(),
                        declaration.desiredState(),
                        grantFor(moduleId, declaration.desiredState()),
                        dependenciesWithin(declaration, byServiceId)))
            .toList();
    return installations.plan(new ManagedServiceInstallation.PlanRequest(requests));
  }

  @Override
  public Optional<ManagedServiceInstallRequest> latestForModule(String moduleId) {
    if (moduleId == null || moduleId.isBlank()) {
      return Optional.empty();
    }
    return installations.recent(RECENT_REQUEST_LIMIT).stream()
        .filter(
            request ->
                request.services().stream()
                    .anyMatch(service -> moduleId.equals(service.moduleId())))
        .findFirst();
  }

  @Override
  public ManagedServiceInstallRequest approveLatest(String moduleId) {
    return installations.allow(pendingForModule(moduleId).requestId());
  }

  @Override
  public ManagedServiceInstallRequest denyLatest(String moduleId) {
    return installations.deny(pendingForModule(moduleId).requestId());
  }

  private ManagedServiceInstallRequest pendingForModule(String moduleId) {
    return latestForModule(moduleId)
        .filter(request -> request.status() == ManagedServiceInstallRequest.Status.PENDING)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Module '" + moduleId + "' has no pending managed-service install request"));
  }

  private static Set<String> dependenciesWithin(
      ManagedServiceDeclaration declaration, Map<String, ManagedServiceDeclaration> byServiceId) {
    for (String dependency : declaration.dependsOn()) {
      if (!byServiceId.containsKey(dependency)) {
        throw new IllegalArgumentException(
            "Managed service '"
                + declaration.serviceId()
                + "' depends on undeclared service '"
                + dependency
                + "'");
      }
    }
    return declaration.dependsOn();
  }

  /** A grant equal to the declared desired state: approval confirms exactly what was declared. */
  private static ManagedServiceResourceGrant grantFor(
      String moduleId, ManagedServiceDesiredState desired) {
    return new ManagedServiceResourceGrant(
        moduleId,
        desired.secretReferences(),
        desired.dataPaths(),
        desired.ports(),
        desired.devices(),
        desired.limits(),
        desired.readinessDeadline(),
        desired.restartLimit());
  }
}
