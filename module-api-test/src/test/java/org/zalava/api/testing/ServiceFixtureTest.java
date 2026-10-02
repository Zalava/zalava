package org.zalava.api.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.RequirementMode;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.api.ZalavaServiceFactory;
import org.zalava.api.ZalavaServiceFactoryContext;
import org.zalava.api.ZalavaServiceRequirement;

class ServiceFixtureTest {
  private static final String MODULE_ID = "fixture-service-module";

  interface Prefix {
    ZalavaServiceContract<Prefix> CONTRACT =
        new ZalavaServiceContract<>("prefix", "1", Prefix.class);

    String apply(String name);
  }

  interface Greeting {
    ZalavaServiceContract<Greeting> CONTRACT =
        new ZalavaServiceContract<>("greeting", "1", Greeting.class);

    String greet(String name);
  }

  interface Other {}

  @Test
  void createsServicesWithScopedConfigurationAndRequiredDependencies() {
    List<String> closed = new ArrayList<>();
    ServiceFixture fixture =
        ServiceFixture.create(
            new FixtureModule(closed),
            ConfigFixture.empty().serviceConfiguration(MODULE_ID, Map.of("suffix", "!")),
            Map.of(Prefix.CONTRACT, (Prefix) name -> "Hello " + name));

    assertThat(fixture.service(Greeting.CONTRACT).greet("Ada")).isEqualTo("Hello Ada!");
    assertThat(fixture.factories()).hasSize(1);
    assertThat(fixture.find(Greeting.CONTRACT)).isPresent();

    fixture.close();
    assertThat(closed).containsExactly("greeting");
  }

  @Test
  void failsClosedWhenARequiredDependencyIsMissing() {
    assertThatThrownBy(
            () ->
                ServiceFixture.create(
                    new FixtureModule(new ArrayList<>()), ConfigFixture.empty(), Map.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reportsAnUnknownProvidedService() {
    try (ServiceFixture fixture =
        ServiceFixture.create(
            new FixtureModule(new ArrayList<>()),
            ConfigFixture.empty(),
            Map.of(Prefix.CONTRACT, (Prefix) name -> name))) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> fixture.service(new ZalavaServiceContract<>("other", "1", Other.class)))
          .withMessage("Module does not provide service: other");
    }
  }

  private static final class FixtureModule implements ZalavaModule {
    private final List<String> closed;

    FixtureModule(List<String> closed) {
      this.closed = closed;
    }

    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor(MODULE_ID, "1.0.0", "Service fixture", "Service fixture");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of();
    }

    @Override
    public List<ZalavaServiceFactory<?>> serviceFactories() {
      return List.of(new GreetingFactory(closed));
    }

    @Override
    public List<ZalavaServiceRequirement> serviceRequirements() {
      return List.of(new ZalavaServiceRequirement("prefix", "1", RequirementMode.REQUIRED));
    }
  }

  private static final class GreetingFactory implements ZalavaServiceFactory<Greeting> {
    private final List<String> closed;

    GreetingFactory(List<String> closed) {
      this.closed = closed;
    }

    @Override
    public ZalavaServiceDescriptor descriptor() {
      return new ZalavaServiceDescriptor("greeting", MODULE_ID, "1");
    }

    @Override
    public ZalavaServiceContract<Greeting> contract() {
      return Greeting.CONTRACT;
    }

    @Override
    public Greeting create(ZalavaServiceFactoryContext context) {
      Prefix prefix =
          context
              .service(Prefix.CONTRACT)
              .orElseThrow(() -> new IllegalStateException("Required prefix service missing"));
      String suffix = String.valueOf(context.configuration().getOrDefault("suffix", ""));
      String authority = context.managedServiceAuthority().moduleId();
      return new FixtureGreeting(prefix, suffix, authority, closed);
    }
  }

  private static final class FixtureGreeting implements Greeting, AutoCloseable {
    private final Prefix prefix;
    private final String suffix;
    private final String authority;
    private final List<String> closed;

    FixtureGreeting(Prefix prefix, String suffix, String authority, List<String> closed) {
      this.prefix = prefix;
      this.suffix = suffix;
      this.authority = authority;
      this.closed = closed;
    }

    @Override
    public String greet(String name) {
      assertThat(authority).isEqualTo(MODULE_ID);
      return prefix.apply(name) + suffix;
    }

    @Override
    public void close() {
      closed.add("greeting");
    }
  }
}
