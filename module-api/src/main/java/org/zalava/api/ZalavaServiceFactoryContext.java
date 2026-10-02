package org.zalava.api;

import java.util.Map;
import java.util.Optional;

/** Scoped service view passed only to a factory whose module declared the dependency. */
public final class ZalavaServiceFactoryContext {

  private final String moduleId;
  private final Map<ZalavaServiceContract<?>, Object> services;
  private final Map<String, ZalavaServiceRequirement> requirements;
  private final Map<String, Object> configuration;
  private final FactorySecretAccess secrets;

  public ZalavaServiceFactoryContext(
      String moduleId,
      Map<ZalavaServiceContract<?>, Object> services,
      Map<String, ZalavaServiceRequirement> requirements) {
    this(moduleId, services, requirements, Map.of(), FactorySecretAccess.none());
  }

  public ZalavaServiceFactoryContext(
      String moduleId,
      Map<ZalavaServiceContract<?>, Object> services,
      Map<String, ZalavaServiceRequirement> requirements,
      Map<String, Object> configuration,
      FactorySecretAccess secrets) {
    if (moduleId == null || moduleId.isBlank())
      throw new IllegalArgumentException("moduleId must not be blank");
    this.moduleId = moduleId;
    this.services = services == null ? Map.of() : Map.copyOf(services);
    this.requirements = requirements == null ? Map.of() : Map.copyOf(requirements);
    this.configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    this.secrets = secrets == null ? FactorySecretAccess.none() : secrets;
  }

  public String moduleId() {
    return moduleId;
  }

  public Map<String, Object> configuration() {
    return configuration;
  }

  public FactorySecretAccess secrets() {
    return secrets;
  }

  /**
   * Returns the authority SEA bound to this module's factory scope for managed-service requests.
   * Modules cannot supply an owner identifier for this authority.
   */
  public ManagedServiceAuthority managedServiceAuthority() {
    return new ManagedServiceAuthority(moduleId);
  }

  public <T> Optional<T> service(ZalavaServiceContract<T> contract) {
    ZalavaServiceRequirement requirement = requirements.get(contract.serviceId());
    if (requirement == null) {
      throw new IllegalStateException(
          "Module " + moduleId + " did not declare service " + contract.serviceId());
    }
    if (!"*".equals(requirement.versionRange())
        && !requirement.versionRange().equals(contract.contractVersion())) {
      throw new IllegalStateException(
          "Module " + moduleId + " did not declare compatible service " + contract.serviceId());
    }
    Object service = services.get(contract);
    return service == null ? Optional.empty() : Optional.of(contract.serviceType().cast(service));
  }
}
