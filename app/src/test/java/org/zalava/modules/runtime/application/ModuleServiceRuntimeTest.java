package org.zalava.modules.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.RequirementMode;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.api.ZalavaServiceFactory;
import org.zalava.api.ZalavaServiceRequirement;

class ModuleServiceRuntimeTest {
  private static final ZalavaServiceContract<Service> A =
      new ZalavaServiceContract<>("service-a", "1", Service.class);
  private static final ZalavaServiceContract<Service> B =
      new ZalavaServiceContract<>("service-b", "1", Service.class);

  @Test
  void createsDependenciesBeforeConsumersAndClosesServicesThenFactoriesInReverseOrder() {
    List<String> events = new ArrayList<>();
    ZalavaModule provider = module("provider", List.of(factory("provider", A, events)), List.of());
    ZalavaModule consumer =
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
        .hasMessageContaining("requires unavailable Zalava service service-a");
    runtime(
            module(
                "optional",
                List.of(),
                List.of(new ZalavaServiceRequirement("missing", "1", RequirementMode.OPTIONAL))))
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
    assertThat(
            runtime.findService(new ZalavaServiceContract<Service>("missing", "1", Service.class)))
        .isEmpty();
    assertThat(
            runtime.findService(
                new ZalavaServiceContract<Service>(A.serviceId(), "2", Service.class)))
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
        .hasMessageContaining("Multiple Zalava service providers");
    ZalavaModule one =
        module("one", List.of(factory("one", A, new ArrayList<>())), List.of(required(B)));
    ZalavaModule two =
        module("two", List.of(factory("two", B, new ArrayList<>())), List.of(required(A)));
    assertThatThrownBy(() -> runtime(one, two))
        .hasMessageContaining("Cyclic Zalava service dependency");
  }

  @Test
  void deniesUndeclaredServiceAccessAndExposesAnOptionalAbsentService() {
    ZalavaModule optional =
        module(
            "optional",
            List.of(),
            List.of(new ZalavaServiceRequirement("missing", "1", RequirementMode.OPTIONAL)));
    ModuleServiceRuntime runtime = runtime(optional);

    assertThat(
            runtime
                .providerContext(org.zalava.api.ProviderFactoryContext.empty())
                .forFactory("optional", "factory")
                .service(new ZalavaServiceContract<>("missing", "1", Service.class)))
        .isEmpty();
    assertThatThrownBy(
            () ->
                runtime
                    .providerContext(org.zalava.api.ProviderFactoryContext.empty())
                    .forFactory("optional", "factory")
                    .service(A))
        .hasMessageContaining("did not declare service service-a");
    runtime.close();
  }

  private static ZalavaServiceRequirement required(ZalavaServiceContract<?> contract) {
    return new ZalavaServiceRequirement(
        contract.serviceId(), contract.contractVersion(), RequirementMode.REQUIRED);
  }

  private static ModuleServiceRuntime runtime(ZalavaModule... modules) {
    return new ModuleServiceRuntime(
        List.of(modules), org.zalava.api.ProviderFactoryContext.empty());
  }

  private static ZalavaModule module(
      String id,
      List<ZalavaServiceFactory<?>> factories,
      List<ZalavaServiceRequirement> requirements) {
    return new ZalavaModule() {
      @Override
      public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(id, "1", id, id);
      }

      @Override
      public List<org.zalava.api.ProviderFactory> providerFactories() {
        return List.of();
      }

      @Override
      public List<ZalavaServiceFactory<?>> serviceFactories() {
        return factories;
      }

      @Override
      public List<ZalavaServiceRequirement> serviceRequirements() {
        return requirements;
      }
    };
  }

  private static ZalavaServiceFactory<Service> factory(
      String owner, ZalavaServiceContract<Service> contract, List<String> events) {
    return new ZalavaServiceFactory<>() {
      @Override
      public ZalavaServiceDescriptor descriptor() {
        return new ZalavaServiceDescriptor(contract.serviceId(), owner, contract.contractVersion());
      }

      @Override
      public ZalavaServiceContract<Service> contract() {
        return contract;
      }

      @Override
      public Service create(org.zalava.api.ZalavaServiceFactoryContext context) {
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
