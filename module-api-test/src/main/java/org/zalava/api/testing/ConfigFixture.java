package org.zalava.api.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.zalava.api.FactorySecretAccess;
import org.zalava.api.ProviderFactoryContext;

/**
 * Builds the scoped configuration, secrets and host services that SEA would supply to a module's
 * provider factories. The fixture mirrors the nested {@code
 * modules.<moduleId>.factories.<factoryId>} shape resolved by {@link
 * ProviderFactoryContext#forFactory(String, String)} without booting SEA.
 */
public final class ConfigFixture {
  private final Map<String, Object> modules = new LinkedHashMap<>();
  private final Map<String, FactorySecretAccess> moduleSecrets = new LinkedHashMap<>();
  private final Map<String, Map<Class<?>, Object>> moduleServices = new LinkedHashMap<>();
  private FactorySecretAccess secrets = FactorySecretAccess.none();

  private ConfigFixture() {}

  public static ConfigFixture empty() {
    return new ConfigFixture();
  }

  /** Declares the configuration block for one provider factory. */
  public ConfigFixture factoryConfiguration(
      String moduleId, String factoryId, Map<String, Object> values) {
    requireText(moduleId, "moduleId");
    requireText(factoryId, "factoryId");
    Objects.requireNonNull(values, "values");
    factoryMap(moduleId).put(factoryId, Map.copyOf(values));
    return this;
  }

  /** Declares the configuration block SEA supplies to one module's service factories. */
  public ConfigFixture serviceConfiguration(String moduleId, Map<String, Object> values) {
    return factoryConfiguration(moduleId, "services", values);
  }

  /** Declares module-scoped secret access used when no factory-specific access is present. */
  public ConfigFixture secrets(String moduleId, FactorySecretAccess access) {
    requireText(moduleId, "moduleId");
    moduleSecrets.put(moduleId, Objects.requireNonNull(access, "access"));
    return this;
  }

  /** Declares the fallback secret access used when a module has no scoped access. */
  public ConfigFixture secrets(FactorySecretAccess access) {
    this.secrets = Objects.requireNonNull(access, "access");
    return this;
  }

  /** Declares an untyped host service (such as {@code TaskService}) for one module. */
  public <T> ConfigFixture hostService(String moduleId, Class<T> type, T service) {
    requireText(moduleId, "moduleId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(service, "service");
    moduleServices.computeIfAbsent(moduleId, ignored -> new LinkedHashMap<>()).put(type, service);
    return this;
  }

  /** Returns a host-faithful provider factory context for the declared values. */
  public ProviderFactoryContext providerContext() {
    return new ProviderFactoryContext(
        Map.of("modules", modules), secrets, moduleSecrets, moduleServices);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> factoryMap(String moduleId) {
    Map<String, Object> module =
        (Map<String, Object>)
            modules.computeIfAbsent(moduleId, ignored -> new LinkedHashMap<String, Object>());
    return (Map<String, Object>)
        module.computeIfAbsent("factories", ignored -> new LinkedHashMap<String, Object>());
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
