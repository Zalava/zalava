package org.zalava.api.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.api.ZalavaServiceFactory;
import org.zalava.api.ZalavaServiceFactoryContext;
import org.zalava.api.ZalavaServiceRequirement;

/**
 * Creates a module's typed services through its {@link ZalavaServiceFactory} declarations with the
 * same scoped configuration, secrets, requirements and already-created services Zalava supplies.
 * Factory and service close in reverse order. Dependency graph resolution itself is host-owned and
 * is not reimplemented here.
 */
public final class ServiceFixture implements AutoCloseable {
  private final Map<ZalavaServiceContract<?>, Object> services;
  private final List<ZalavaServiceFactory<?>> factories;
  private final List<AutoCloseable> created;

  private ServiceFixture(
      Map<ZalavaServiceContract<?>, Object> services,
      List<ZalavaServiceFactory<?>> factories,
      List<AutoCloseable> created) {
    this.services = services;
    this.factories = factories;
    this.created = created;
  }

  public static ServiceFixture create(ZalavaModule module, ConfigFixture configuration) {
    return create(module, configuration, Map.of());
  }

  /**
   * Creates the module's services, exposing the supplied dependency services to factories that
   * declare them as requirements.
   */
  public static ServiceFixture create(
      ZalavaModule module,
      ConfigFixture configuration,
      Map<ZalavaServiceContract<?>, Object> availableServices) {
    Objects.requireNonNull(module, "module");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(availableServices, "availableServices");
    String moduleId = module.descriptor().moduleId();
    ProviderFactoryContext scoped =
        configuration.providerContext().forFactory(moduleId, "services");
    Map<ZalavaServiceContract<?>, Object> services = new LinkedHashMap<>(availableServices);
    Map<String, ZalavaServiceRequirement> requirements = new LinkedHashMap<>();
    for (ZalavaServiceRequirement requirement : module.serviceRequirements()) {
      requirements.put(requirement.serviceId(), requirement);
    }
    List<ZalavaServiceFactory<?>> factories = new ArrayList<>();
    List<AutoCloseable> created = new ArrayList<>();
    try {
      for (ZalavaServiceFactory<?> factory : module.serviceFactories()) {
        ZalavaServiceFactoryContext context =
            new ZalavaServiceFactoryContext(
                moduleId, services, requirements, scoped.configuration(), scoped.secrets());
        Object service = factory.create(context);
        ZalavaServiceContract<?> contract = factory.contract();
        if (contract == null || !contract.serviceType().isInstance(service)) {
          throw new IllegalStateException(
              "Module service factory returned an incompatible service");
        }
        services.put(contract, service);
        factories.add(factory);
        if (service instanceof AutoCloseable closeable) {
          created.add(closeable);
        }
      }
    } catch (RuntimeException exception) {
      closeQuietly(factories, created);
      throw exception;
    }
    return new ServiceFixture(services, factories, created);
  }

  /** Factories that were created, in declaration order. */
  public List<ZalavaServiceFactory<?>> factories() {
    return List.copyOf(factories);
  }

  public <T> Optional<T> find(ZalavaServiceContract<T> contract) {
    Objects.requireNonNull(contract, "contract");
    Object service = services.get(contract);
    return service == null ? Optional.empty() : Optional.of(contract.serviceType().cast(service));
  }

  public <T> T service(ZalavaServiceContract<T> contract) {
    return find(contract)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Module does not provide service: " + contract.serviceId()));
  }

  @Override
  public void close() {
    closeQuietly(factories, created);
  }

  private static void closeQuietly(
      List<ZalavaServiceFactory<?>> factories, List<AutoCloseable> created) {
    for (int index = created.size() - 1; index >= 0; index--) {
      try {
        created.get(index).close();
      } catch (Exception ignored) {
        // Best-effort close mirrors the host's reverse-order shutdown.
      }
    }
    for (int index = factories.size() - 1; index >= 0; index--) {
      try {
        factories.get(index).close();
      } catch (Exception ignored) {
        // Best-effort close mirrors the host's reverse-order shutdown.
      }
    }
  }
}
