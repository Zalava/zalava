package org.zalava;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Scoped configuration, secrets, and declared host services for one provider factory. */
public final class ProviderFactoryContext {
  private final Map<String, Object> configuration;
  private final FactorySecretAccess secrets;
  private final Map<String, FactorySecretAccess> moduleSecrets;
  private final Map<String, Map<Class<?>, Object>> moduleServices;
  private final Map<String, ZalavaServiceFactoryContext> typedServices;
  private final ZalavaServiceFactoryContext scopedTypedServices;

  public ProviderFactoryContext(
      Map<String, Object> configuration,
      FactorySecretAccess secrets,
      Map<String, FactorySecretAccess> moduleSecrets,
      Map<String, Map<Class<?>, Object>> moduleServices) {
    this(configuration, secrets, moduleSecrets, moduleServices, Map.of(), null);
  }

  private ProviderFactoryContext(
      Map<String, Object> configuration,
      FactorySecretAccess secrets,
      Map<String, FactorySecretAccess> moduleSecrets,
      Map<String, Map<Class<?>, Object>> moduleServices,
      Map<String, ZalavaServiceFactoryContext> typedServices,
      ZalavaServiceFactoryContext scopedTypedServices) {
    this.configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    this.secrets = secrets == null ? FactorySecretAccess.none() : secrets;
    this.moduleSecrets = moduleSecrets == null ? Map.of() : Map.copyOf(moduleSecrets);
    this.moduleServices = moduleServices == null ? Map.of() : Map.copyOf(moduleServices);
    this.typedServices = typedServices == null ? Map.of() : Map.copyOf(typedServices);
    this.scopedTypedServices = scopedTypedServices;
  }

  public ProviderFactoryContext(Map<String, Object> configuration, FactorySecretAccess secrets) {
    this(configuration, secrets, Map.of(), Map.of());
  }

  public ProviderFactoryContext(
      Map<String, Object> configuration,
      FactorySecretAccess secrets,
      Map<String, FactorySecretAccess> moduleSecrets) {
    this(configuration, secrets, moduleSecrets, Map.of());
  }

  public ProviderFactoryContext(Map<String, Object> configuration) {
    this(configuration, FactorySecretAccess.none());
  }

  public static ProviderFactoryContext empty() {
    return new ProviderFactoryContext(Map.of());
  }

  public Map<String, Object> configuration() {
    return configuration;
  }

  public FactorySecretAccess secrets() {
    return secrets;
  }

  public Map<String, FactorySecretAccess> moduleSecrets() {
    return moduleSecrets;
  }

  public Map<String, Map<Class<?>, Object>> moduleServices() {
    return moduleServices;
  }

  public ProviderFactoryContext withTypedServices(
      Map<String, ZalavaServiceFactoryContext> services) {
    return new ProviderFactoryContext(
        configuration, secrets, moduleSecrets, moduleServices, services, null);
  }

  @SuppressWarnings("unchecked")
  public ProviderFactoryContext forFactory(String moduleId, String factoryId) {
    requireText(moduleId, "moduleId");
    requireText(factoryId, "factoryId");
    Object modules = configuration.get("modules");
    if (!(modules instanceof Map<?, ?> moduleValues))
      return scopedFactoryContext(moduleId, Map.of());
    Object module = moduleValues.get(moduleId);
    if (!(module instanceof Map<?, ?> moduleConfiguration))
      return scopedFactoryContext(moduleId, Map.of());
    Object factories = moduleConfiguration.get("factories");
    if (!(factories instanceof Map<?, ?> factoryValues))
      return scopedFactoryContext(moduleId, Map.of());
    Object factory = factoryValues.get(factoryId);
    if (!(factory instanceof Map<?, ?> factoryConfiguration))
      return scopedFactoryContext(moduleId, Map.of());
    return scopedFactoryContext(moduleId, (Map<String, Object>) factoryConfiguration);
  }

  public <T> Optional<T> service(Class<T> type) {
    for (Map<Class<?>, Object> services : moduleServices.values()) {
      Object service = services.get(type);
      if (type.isInstance(service)) return Optional.of(type.cast(service));
    }
    return Optional.empty();
  }

  public <T> Optional<T> service(ZalavaServiceContract<T> contract) {
    if (scopedTypedServices == null)
      throw new IllegalStateException("No typed SEA service scope is available");
    return scopedTypedServices.service(contract);
  }

  private ProviderFactoryContext scopedFactoryContext(
      String moduleId, Map<String, Object> factoryConfiguration) {
    return new ProviderFactoryContext(
        factoryConfiguration,
        moduleSecrets.getOrDefault(moduleId, secrets),
        Map.of(),
        Map.of(moduleId, moduleServices.getOrDefault(moduleId, Map.of())),
        Map.of(),
        typedServices.get(moduleId));
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(Objects.requireNonNull(name) + " must not be blank");
  }
}
