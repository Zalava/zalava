package org.zalava.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.zalava.ProviderFactoryContext;
import org.zalava.SeaModule;
import org.zalava.SeaServiceContract;
import org.zalava.SeaServiceFactory;
import org.zalava.SeaServiceFactoryContext;
import org.zalava.SeaServiceRequirement;

/**
 * Creates a module's typed services through its {@link SeaServiceFactory} declarations with the
 * same scoped configuration, secrets, requirements and already-created services SEA supplies.
 * Factory and service close in reverse order. Dependency graph resolution itself is host-owned and
 * is not reimplemented here.
 */
public final class ServiceFixture implements AutoCloseable {
  private final Map<SeaServiceContract<?>, Object> services;
  private final List<SeaServiceFactory<?>> factories;
  private final List<AutoCloseable> created;

  private ServiceFixture(
      Map<SeaServiceContract<?>, Object> services,
      List<SeaServiceFactory<?>> factories,
      List<AutoCloseable> created) {
    this.services = services;
    this.factories = factories;
    this.created = created;
  }

  public static ServiceFixture create(SeaModule module, ConfigFixture configuration) {
    return create(module, configuration, Map.of());
  }

  /**
   * Creates the module's services, exposing the supplied dependency services to factories that
   * declare them as requirements.
   */
  public static ServiceFixture create(
      SeaModule module,
      ConfigFixture configuration,
      Map<SeaServiceContract<?>, Object> availableServices) {
    Objects.requireNonNull(module, "module");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(availableServices, "availableServices");
    String moduleId = module.descriptor().moduleId();
    ProviderFactoryContext scoped =
        configuration.providerContext().forFactory(moduleId, "services");
    Map<SeaServiceContract<?>, Object> services = new LinkedHashMap<>(availableServices);
    Map<String, SeaServiceRequirement> requirements = new LinkedHashMap<>();
    for (SeaServiceRequirement requirement : module.serviceRequirements()) {
      requirements.put(requirement.serviceId(), requirement);
    }
    List<SeaServiceFactory<?>> factories = new ArrayList<>();
    List<AutoCloseable> created = new ArrayList<>();
    try {
      for (SeaServiceFactory<?> factory : module.serviceFactories()) {
        SeaServiceFactoryContext context =
            new SeaServiceFactoryContext(
                moduleId, services, requirements, scoped.configuration(), scoped.secrets());
        Object service = factory.create(context);
        SeaServiceContract<?> contract = factory.contract();
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
  public List<SeaServiceFactory<?>> factories() {
    return List.copyOf(factories);
  }

  public <T> Optional<T> find(SeaServiceContract<T> contract) {
    Objects.requireNonNull(contract, "contract");
    Object service = services.get(contract);
    return service == null ? Optional.empty() : Optional.of(contract.serviceType().cast(service));
  }

  public <T> T service(SeaServiceContract<T> contract) {
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
      List<SeaServiceFactory<?>> factories, List<AutoCloseable> created) {
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
