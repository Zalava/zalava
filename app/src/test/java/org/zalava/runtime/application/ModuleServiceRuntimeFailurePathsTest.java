package org.zalava.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactoryContext;
import org.zalava.RequirementMode;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaServiceContract;
import org.zalava.ZalavaServiceDescriptor;
import org.zalava.ZalavaServiceFactory;
import org.zalava.ZalavaServiceRequirement;

class ModuleServiceRuntimeFailurePathsTest {

  private static final ZalavaServiceContract<Service> A =
      new ZalavaServiceContract<>("service-a", "1", Service.class);

  @Test
  void versionMismatchAndWildcardRangesDecideAvailability() {
    ZalavaModule consumer =
        module(
            "consumer",
            List.of(),
            List.of(new ZalavaServiceRequirement("service-a", "9", RequirementMode.REQUIRED)));

    assertThatThrownBy(() -> runtime(consumer, provider()))
        .hasMessageContaining("requires unavailable SEA service service-a");

    ZalavaModule rangedConsumer =
        module(
            "ranged",
            List.of(),
            List.of(new ZalavaServiceRequirement("service-a", "*", RequirementMode.REQUIRED)));
    ModuleServiceRuntime ranged = runtime(rangedConsumer, provider());
    assertThat(
            ranged
                .providerContext(ProviderFactoryContext.empty())
                .forFactory("ranged", "services")
                .service(A))
        .isPresent();
    ranged.close();
  }

  @Test
  void anIncompatibleFactoryResultFailsClosedAndClosesCreatedServices() {
    ZalavaModule provider =
        module(
            "provider",
            List.of(
                new ZalavaServiceFactory<Service>() {
                  @Override
                  public ZalavaServiceDescriptor descriptor() {
                    return new ZalavaServiceDescriptor("service-a", "provider", "1");
                  }

                  @Override
                  public ZalavaServiceContract<Service> contract() {
                    return A;
                  }

                  @Override
                  public Service create(org.zalava.ZalavaServiceFactoryContext context) {
                    return new Service("service-a", new ArrayList<>());
                  }
                }),
            List.of());
    ZalavaModule incompatibleConsumer =
        module(
            "consumer",
            List.of(
                new ZalavaServiceFactory<Service>() {
                  @Override
                  public ZalavaServiceDescriptor descriptor() {
                    return new ZalavaServiceDescriptor("service-b", "consumer", "1");
                  }

                  @Override
                  public ZalavaServiceContract<Service> contract() {
                    return new ZalavaServiceContract<>("service-b", "1", Service.class);
                  }

                  @Override
                  public Service create(org.zalava.ZalavaServiceFactoryContext context) {
                    // Returns null, which is not an instance of Service.
                    return null;
                  }
                }),
            List.of(new ZalavaServiceRequirement("service-a", "1", RequirementMode.REQUIRED)));

    assertThatThrownBy(() -> runtime(incompatibleConsumer, provider))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("returned an incompatible implementation for service-b");
  }

  @Test
  void factoriesWithoutADescriptorOrContractAreRejected() {
    ZalavaModule broken =
        module(
            "broken",
            List.of(
                new ZalavaServiceFactory<Service>() {
                  @Override
                  public ZalavaServiceDescriptor descriptor() {
                    return null;
                  }

                  @Override
                  public ZalavaServiceContract<Service> contract() {
                    return A;
                  }

                  @Override
                  public Service create(org.zalava.ZalavaServiceFactoryContext context) {
                    return new Service("service-a", new ArrayList<>());
                  }
                }),
            List.of());

    assertThatThrownBy(() -> runtime(broken))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("SEA service factories must declare a descriptor and contract");
  }

  @Test
  void descriptorsOwnedByAnotherModuleAreRejected() {
    ZalavaModule mismatched =
        module("other-module", List.of(factory("provider", A, new ArrayList<>())), List.of());

    assertThatThrownBy(() -> runtime(mismatched))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is declared by the wrong module");
  }

  @Test
  void descriptorAndContractDisagreementIsRejected() {
    List<String> events = new ArrayList<>();
    ZalavaServiceFactory<Service> factory =
        new ZalavaServiceFactory<>() {
          @Override
          public ZalavaServiceDescriptor descriptor() {
            return new ZalavaServiceDescriptor("service-a", "module", "2");
          }

          @Override
          public ZalavaServiceContract<Service> contract() {
            return A;
          }

          @Override
          public Service create(org.zalava.ZalavaServiceFactoryContext context) {
            return new Service("service-a", events);
          }
        };
    ZalavaModule module = module("module", List.of(factory), List.of());

    assertThatThrownBy(() -> runtime(module))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not match contract");
  }

  @Test
  void duplicateRequirementsInsideOneModuleAreRejected() {
    ZalavaModule module =
        module(
            "consumer",
            List.of(),
            List.of(
                new ZalavaServiceRequirement("service-a", "1", RequirementMode.REQUIRED),
                new ZalavaServiceRequirement("service-a", "*", RequirementMode.OPTIONAL)));

    assertThatThrownBy(() -> runtime(module, provider()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares duplicate SEA service service-a");
  }

  @Test
  void closeWithAFailingCloseableSuppressesAndReportsTheFailure() {
    List<String> events = new ArrayList<>();
    ZalavaServiceFactory<Service> factory =
        new ZalavaServiceFactory<>() {
          @Override
          public ZalavaServiceDescriptor descriptor() {
            return new ZalavaServiceDescriptor("service-a", "module", "1");
          }

          @Override
          public ZalavaServiceContract<Service> contract() {
            return A;
          }

          @Override
          public Service create(org.zalava.ZalavaServiceFactoryContext context) {
            return new Service("service-a", events) {
              @Override
              public void close() {
                throw new IllegalStateException("close failed");
              }
            };
          }

          @Override
          public void close() {
            events.add("close-factory-service-a");
          }
        };
    ModuleServiceRuntime runtime = runtime(module("module", List.of(factory), List.of()));

    assertThatThrownBy(runtime::close)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to close SEA services")
        .hasSuppressedException(new IllegalStateException("close failed"));
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
      public Service create(org.zalava.ZalavaServiceFactoryContext context) {
        events.add("create-" + contract.serviceId());
        return new Service(contract.serviceId(), events);
      }
    };
  }

  private static ZalavaModule provider() {
    return module("provider", List.of(factory("provider", A, new ArrayList<>())), List.of());
  }

  private static ModuleServiceRuntime runtime(ZalavaModule... modules) {
    return new ModuleServiceRuntime(List.of(modules), ProviderFactoryContext.empty());
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
      public List<org.zalava.ProviderFactory> providerFactories() {
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

  private static class Service implements AutoCloseable {
    private final String id;
    private final List<String> events;

    Service(String id, List<String> events) {
      this.id = id;
      this.events = events;
    }

    @Override
    public void close() {
      events.add("close-" + id);
    }
  }
}
