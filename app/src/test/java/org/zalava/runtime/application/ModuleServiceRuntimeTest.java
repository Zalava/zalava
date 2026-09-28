package org.zalava.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.ModuleDescriptor;
import org.zalava.RequirementMode;
import org.zalava.SeaModule;
import org.zalava.SeaServiceContract;
import org.zalava.SeaServiceDescriptor;
import org.zalava.SeaServiceFactory;
import org.zalava.SeaServiceRequirement;

class ModuleServiceRuntimeTest {
  private static final SeaServiceContract<Service> A =
      new SeaServiceContract<>("service-a", "1", Service.class);
  private static final SeaServiceContract<Service> B =
      new SeaServiceContract<>("service-b", "1", Service.class);

  @Test
  void createsDependenciesBeforeConsumersAndClosesServicesThenFactoriesInReverseOrder() {
    List<String> events = new ArrayList<>();
    SeaModule provider = module("provider", List.of(factory("provider", A, events)), List.of());
    SeaModule consumer =
        module("consumer", List.of(factory("consumer", B, events)), List.of(required(A)));

    ModuleServiceRuntime runtime = runtime(consumer, provider);
    runtime.close();

    assertThat(events)
        .containsSubsequence(
            "create-service-a",
            "create-service-b",
            "close-service-b",
            "close-factory-service-b",
            "close-service-a",
            "close-factory-service-a");
  }

  @Test
  void rejectsMissingRequiredServiceAndAllowsOptionalService() {
    assertThatThrownBy(() -> runtime(module("consumer", List.of(), List.of(required(A)))))
        .hasMessageContaining("requires unavailable SEA service service-a");
    runtime(
            module(
                "optional",
                List.of(),
                List.of(new SeaServiceRequirement("missing", "1", RequirementMode.OPTIONAL))))
        .close();
  }

  @Test
  void findsOnlyTheActiveServiceMatchingItsTypedContract() {
    List<String> events = new ArrayList<>();
    ModuleServiceRuntime runtime =
        runtime(module("provider", List.of(factory("provider", A, events)), List.of()));

    assertThat(runtime.findService(A))
        .hasValueSatisfying(
            loaded -> {
              assertThat(loaded.descriptor().serviceId()).isEqualTo(A.serviceId());
              assertThat(loaded.service()).isInstanceOf(Service.class);
            });
    assertThat(runtime.findService(new SeaServiceContract<Service>("missing", "1", Service.class)))
        .isEmpty();
    assertThat(
            runtime.findService(new SeaServiceContract<Service>(A.serviceId(), "2", Service.class)))
        .isEmpty();

    runtime.close();
  }

  @Test
  void rejectsDuplicateProvidersAndCyclesBeforeCreation() {
    assertThatThrownBy(
            () ->
                runtime(
                    module("one", List.of(factory("one", A, new ArrayList<>())), List.of()),
                    module("two", List.of(factory("two", A, new ArrayList<>())), List.of())))
        .hasMessageContaining("Multiple SEA service providers");
    SeaModule one =
        module("one", List.of(factory("one", A, new ArrayList<>())), List.of(required(B)));
    SeaModule two =
        module("two", List.of(factory("two", B, new ArrayList<>())), List.of(required(A)));
    assertThatThrownBy(() -> runtime(one, two))
        .hasMessageContaining("Cyclic SEA service dependency");
  }

  @Test
  void deniesUndeclaredServiceAccessAndExposesAnOptionalAbsentService() {
    SeaModule optional =
        module(
            "optional",
            List.of(),
            List.of(new SeaServiceRequirement("missing", "1", RequirementMode.OPTIONAL)));
    ModuleServiceRuntime runtime = runtime(optional);

    assertThat(
            runtime
                .providerContext(org.zalava.ProviderFactoryContext.empty())
                .forFactory("optional", "factory")
                .service(new SeaServiceContract<>("missing", "1", Service.class)))
        .isEmpty();
    assertThatThrownBy(
            () ->
                runtime
                    .providerContext(org.zalava.ProviderFactoryContext.empty())
                    .forFactory("optional", "factory")
                    .service(A))
        .hasMessageContaining("did not declare service service-a");
    runtime.close();
  }

  private static SeaServiceRequirement required(SeaServiceContract<?> contract) {
    return new SeaServiceRequirement(
        contract.serviceId(), contract.contractVersion(), RequirementMode.REQUIRED);
  }

  private static ModuleServiceRuntime runtime(SeaModule... modules) {
    return new ModuleServiceRuntime(List.of(modules), org.zalava.ProviderFactoryContext.empty());
  }

  private static SeaModule module(
      String id, List<SeaServiceFactory<?>> factories, List<SeaServiceRequirement> requirements) {
    return new SeaModule() {
      @Override
      public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(id, "1", id, id);
      }

      @Override
      public List<org.zalava.ProviderFactory> providerFactories() {
        return List.of();
      }

      @Override
      public List<SeaServiceFactory<?>> serviceFactories() {
        return factories;
      }

      @Override
      public List<SeaServiceRequirement> serviceRequirements() {
        return requirements;
      }
    };
  }

  private static SeaServiceFactory<Service> factory(
      String owner, SeaServiceContract<Service> contract, List<String> events) {
    return new SeaServiceFactory<>() {
      @Override
      public SeaServiceDescriptor descriptor() {
        return new SeaServiceDescriptor(contract.serviceId(), owner, contract.contractVersion());
      }

      @Override
      public SeaServiceContract<Service> contract() {
        return contract;
      }

      @Override
      public Service create(org.zalava.SeaServiceFactoryContext context) {
        events.add("create-" + contract.serviceId());
        return new Service(contract.serviceId(), events);
      }

      @Override
      public void close() {
        events.add("close-factory-" + contract.serviceId());
      }
    };
  }

  private record Service(String id, List<String> events) implements AutoCloseable {
    @Override
    public void close() {
      events.add("close-" + id);
    }
  }
}
