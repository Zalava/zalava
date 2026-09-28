package org.zalava.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.SeaModule;
import org.zalava.web.application.DefaultWebExtensionRoutes;
import org.zalava.web.application.port.out.WebExtensionModuleCatalog;
import org.junit.jupiter.api.Test;

class SeaWebExtensionCatalogTest {

  @Test
  void registersAndResolvesModuleRoutesUnderPageNamespace() {
    DefaultWebExtensionRoutes catalog = catalog(new TestModule(List.of(new TestExtension())));

    assertThat(catalog.pages())
        .hasSize(1)
        .first()
        .satisfies(
            page -> {
              assertThat(page.moduleId()).isEqualTo("test-module");
              assertThat(page.pageId()).isEqualTo("fixture-page");
              assertThat(page.path()).isEqualTo("/apps/test-module/fixture-page");
            });
    assertThat(catalog.resolve("GET", "test-module", "fixture-page", "/")).isPresent();
    assertThat(catalog.resolve("POST", "test-module", "fixture-page", "/items/42/bought"))
        .isPresent()
        .get()
        .extracting(invocation -> invocation.pathVariables().get("id"))
        .isEqualTo("42");
    assertThat(catalog.resolve("GET", "test-module", "fixture-page", "/items/42/bought")).isEmpty();
  }

  @Test
  void rejectsExtensionForDifferentModule() {
    SeaWebExtension extension =
        new SeaWebExtension() {
          @Override
          public WebExtensionDescriptor descriptor() {
            return new WebExtensionDescriptor(
                "other-module", "bad-extension", "Bad", "Bad extension.");
          }

          @Override
          public void register(WebExtensionRegistry registry) {
            registry.page("bad").get("/", request -> SeaWebResponse.html("bad"));
          }
        };

    assertThatThrownBy(() -> catalog(new TestModule(List.of(extension))).pages())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "SEA web extension bad-extension belongs to module other-module "
                + "but owning module is test-module");
  }

  @Test
  void rejectsUnsafePageIdsAndRoutePaths() {
    SeaWebExtension unsafePage =
        new SeaWebExtension() {
          @Override
          public WebExtensionDescriptor descriptor() {
            return new WebExtensionDescriptor(
                "test-module", "bad-extension", "Bad", "Bad extension.");
          }

          @Override
          public void register(WebExtensionRegistry registry) {
            registry.page("../chat").get("/", request -> SeaWebResponse.html("bad"));
          }
        };
    SeaWebExtension unsafeRoute =
        new SeaWebExtension() {
          @Override
          public WebExtensionDescriptor descriptor() {
            return new WebExtensionDescriptor("test-module", "bad-route", "Bad", "Bad extension.");
          }

          @Override
          public void register(WebExtensionRegistry registry) {
            registry.page("bad").get("../chat", request -> SeaWebResponse.html("bad"));
          }
        };

    assertThatThrownBy(() -> catalog(new TestModule(List.of(unsafePage))).pages())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("web extension page id must be a lowercase route segment");
    assertThatThrownBy(() -> catalog(new TestModule(List.of(unsafeRoute))).pages())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SEA web extension route path is invalid: ../chat");
  }

  private static DefaultWebExtensionRoutes catalog(SeaModule module) {
    WebExtensionModuleCatalog moduleCatalog = () -> List.of(module);
    return new DefaultWebExtensionRoutes(moduleCatalog);
  }

  private static final class TestModule implements SeaModule {

    private final List<SeaWebExtension> extensions;

    private TestModule(List<SeaWebExtension> extensions) {
      this.extensions = extensions;
    }

    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor("test-module", "1.0.0", "Test Module", "Module for tests.");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of();
    }

    @Override
    public List<SeaWebExtension> webExtensions() {
      return extensions;
    }
  }

  private static final class TestExtension implements SeaWebExtension {

    @Override
    public WebExtensionDescriptor descriptor() {
      return new WebExtensionDescriptor(
          "test-module", "fixture-extension", "Fixture Extension", "Fixture extension.");
    }

    @Override
    public void register(WebExtensionRegistry registry) {
      registry
          .page("fixture-page")
          .title("Fixture Page")
          .description("Fixture page.")
          .get("/", request -> SeaWebResponse.html("ok"))
          .post("/items/{id}/bought", request -> SeaWebResponse.html("ok"));
    }
  }
}
