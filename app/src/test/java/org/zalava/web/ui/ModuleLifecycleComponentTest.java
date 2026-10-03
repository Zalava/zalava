package org.zalava.web.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.api.ModuleConfigurationDescriptor;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.runtime.StaticZalavaModuleRegistry;
import org.zalava.modules.runtime.ZalavaModuleRegistry;
import org.zalava.support.AuthenticatedZalavaComponentTest;
import org.zalava.support.ComponentTestAccounts;

@AuthenticatedZalavaComponentTest
@Import(ModuleLifecycleComponentTest.FixtureConfiguration.class)
class ModuleLifecycleComponentTest {
  private static final Path WORKSPACE = workspace();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("zalava.module-configuration.root", WORKSPACE::toString);
  }

  private static Path workspace() {
    try {
      return Files.createTempDirectory("zalava-module-lifecycle-component-");
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ComponentTestAccounts accounts;

  @Test
  void configurationAndStartStopAreVisibleThroughTheModulePage() throws Exception {
    mvc.perform(get("/modules/lifecycle-fixture"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-runtime-state=\"SETUP_REQUIRED\"")));

    mvc.perform(post("/modules/lifecycle-fixture/start").with(csrf()))
        .andExpect(status().is3xxRedirection());
    mvc.perform(get("/modules/lifecycle-fixture"))
        .andExpect(content().string(containsString("data-runtime-state=\"SETUP_REQUIRED\"")));

    mvc.perform(
            post("/modules/lifecycle-fixture/configuration")
                .with(csrf())
                .param("configuration.fixture.endpoint", "ready"))
        .andExpect(status().is3xxRedirection());
    mvc.perform(post("/modules/lifecycle-fixture/start").with(csrf()))
        .andExpect(status().is3xxRedirection());
    mvc.perform(get("/modules/lifecycle-fixture"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-runtime-state=\"RUNNING\"")))
        .andExpect(content().string(containsString("Stop module")));

    mvc.perform(post("/modules/lifecycle-fixture/stop").with(csrf()))
        .andExpect(status().is3xxRedirection());
    mvc.perform(get("/modules/lifecycle-fixture"))
        .andExpect(content().string(containsString("data-runtime-state=\"STOPPED\"")));
  }

  @Test
  void memberCannotStartModule() throws Exception {
    var member = accounts.newActivated(AccountRole.MEMBER);
    mvc.perform(
            post("/modules/lifecycle-fixture/start")
                .with(csrf())
                .with(accounts.authenticatedAs(member)))
        .andExpect(status().isForbidden());
    mvc.perform(post("/modules/lifecycle-fixture/start")).andExpect(status().is3xxRedirection());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixtureConfiguration {
    @Bean
    @Primary
    ZalavaModuleRegistry lifecycleFixtureRegistry(Set<ZalavaModule> builtIns) {
      List<ZalavaModule> modules = new ArrayList<>(builtIns);
      modules.add(new FixtureModule());
      return new StaticZalavaModuleRegistry(modules);
    }

    @Bean
    @Primary
    ModuleQueries lifecycleFixtureQueries() {
      return () ->
          List.of(
              new ModuleQueries.EnabledModule(
                  "lifecycle-fixture", "1.0.0", "fixture", "fixture", List.of()));
    }
  }

  private static class FixtureModule implements ZalavaModule {
    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor(
          "lifecycle-fixture", "1.0.0", "Lifecycle fixture", "Lifecycle fixture");
    }

    @Override
    public ModuleConfigurationDescriptor configuration() {
      return new ModuleConfigurationDescriptor(
          Map.of(
              "type",
              "object",
              "properties",
              Map.of(
                  "fixture",
                  Map.of(
                      "type",
                      "object",
                      "properties",
                      Map.of("endpoint", Map.of("type", "string"))))));
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of(
          new ProviderFactory() {
            @Override
            public ProviderFactoryDescriptor descriptor() {
              return new ProviderFactoryDescriptor(
                  "fixture", "lifecycle-fixture", "fixture", "Fixture", "Fixture");
            }

            @Override
            public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
              if (!"ready".equals(context.configuration().get("endpoint"))) {
                throw new IllegalStateException("Endpoint is not configured");
              }
              return List.of();
            }
          });
    }
  }
}
