package org.zalava.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactoryContext;
import org.zalava.RequirementMode;
import org.zalava.SeaModule;
import org.zalava.SeaServiceContract;
import org.zalava.SeaServiceDescriptor;
import org.zalava.SeaServiceFactory;
import org.zalava.SeaServiceRequirement;
import org.junit.jupiter.api.Test;

class ModuleServiceRuntimeFailurePathsTest {

  private static final SeaServiceContract<Service> A =
      new SeaServiceContract<>("service-a", "1", Service.class);

  @Test
  void versionMismatchAndWildcardRangesDecideAvailability() {
    SeaModule consumer =
        module(
            "consumer",
            List.of(),
            List.of(new SeaServiceRequirement("service-a", "9", RequirementMode.REQUIRED)));

    assertThatThrownBy(() -> runtime(consumer, provider()))
        .hasMessageContaining("requires unavailable SEA service service-a");

    SeaModule rangedConsumer =
        module(
            "ranged",
            List.of(),
            List.of(new SeaServiceRequirement("service-a", "*", RequirementMode.REQUIRED)));
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
    SeaModule provider =
        module(
            "provider",
            List.of(
                new SeaServiceFactory<Service>() {
                  @Override
                  public SeaServiceDescriptor descriptor() {
                    return new SeaServiceDescriptor("service-a", "provider", "1");
                  }

                  @Override
                  public SeaServiceContract<Service> contract() {
                    return A;
                  }

                  @Override
                  public Service create(org.zalava.SeaServiceFactoryContext context) {
                    return new Service("service-a", new ArrayList<>());
                  }
                }),
            List.of());
    SeaModule incompatibleConsumer =
        module(
            "consumer",
            List.of(
                new SeaServiceFactory<Service>() {
                  @Override
                  public SeaServiceDescriptor descriptor() {
                    return new SeaServiceDescriptor("service-b", "consumer", "1");
                  }

                  @Override
                  public SeaServiceContract<Service> contract() {
                    return new SeaServiceContract<>("service-b", "1", Service.class);
                  }

                  @Override
                  public Service create(org.zalava.SeaServiceFactoryContext context) {
                    // Returns null, which is not an instance of Service.
                    return null;
                  }
                }),
            List.of(new SeaServiceRequirement("service-a", "1", RequirementMode.REQUIRED)));

    assertThatThrownBy(() -> runtime(incompatibleConsumer, provider))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("returned an incompatible implementation for service-b");
  }

  @Test
  void factoriesWithoutADescriptorOrContractAreRejected() {
    SeaModule broken =
        module(
            "broken",
            List.of(
                new SeaServiceFactory<Service>() {
                  @Override
                  public SeaServiceDescriptor descriptor() {
                    return null;
                  }

                  @Override
                  public SeaServiceContract<Service> contract() {
                    return A;
                  }

                  @Override
                  public Service create(org.zalava.SeaServiceFactoryContext context) {
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
    SeaModule mismatched =
        module("other-module", List.of(factory("provider", A, new ArrayList<>())), List.of());

    assertThatThrownBy(() -> runtime(mismatched))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is declared by the wrong module");
  }

  @Test
  void descriptorAndContractDisagreementIsRejected() {
    List<String> events = new ArrayList<>();
    SeaServiceFactory<Service> factory =
        new SeaServiceFactory<>() {
          @Override
          public SeaServiceDescriptor descriptor() {
            return new SeaServiceDescriptor("service-a", "module", "2");
          }

          @Override
          public SeaServiceContract<Service> contract() {
            return A;
          }

          @Override
          public Service create(org.zalava.SeaServiceFactoryContext context) {
            return new Service("service-a", events);
          }
        };
    SeaModule module = module("module", List.of(factory), List.of());

    assertThatThrownBy(() -> runtime(module))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not match contract");
  }

  @Test
  void duplicateRequirementsInsideOneModuleAreRejected() {
    SeaModule module =
        module(
            "consumer",
            List.of(),
            List.of(
                new SeaServiceRequirement("service-a", "1", RequirementMode.REQUIRED),
                new SeaServiceRequirement("service-a", "*", RequirementMode.OPTIONAL)));

    assertThatThrownBy(() -> runtime(module, provider()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares duplicate SEA service service-a");
  }

  @Test
  void closeWithAFailingCloseableSuppressesAndReportsTheFailure() {
    List<String> events = new ArrayList<>();
    SeaServiceFactory<Service> factory =
        new SeaServiceFactory<>() {
          @Override
          public SeaServiceDescriptor descriptor() {
            return new SeaServiceDescriptor("service-a", "module", "1");
          }

          @Override
          public SeaServiceContract<Service> contract() {
            return A;
          }

          @Override
          public Service create(org.zalava.SeaServiceFactoryContext context) {
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
    };
  }

  private static SeaModule provider() {
    return module("provider", List.of(factory("provider", A, new ArrayList<>())), List.of());
  }

  private static ModuleServiceRuntime runtime(SeaModule... modules) {
    return new ModuleServiceRuntime(List.of(modules), ProviderFactoryContext.empty());
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
