package org.zalava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.SeaModule;
import org.zalava.web.SeaWebExtension;
import org.zalava.web.SeaWebRequest;
import org.zalava.web.SeaWebResponse;
import org.zalava.web.WebExtensionDescriptor;
import org.zalava.web.WebExtensionRegistry;

class WebExtensionFixtureTest {
  private static final String MODULE_ID = "fixture-web-module";

  @Test
  void registersPagesRoutesAndInvokesHandlersWithSyntheticRequests() {
    WebExtensionFixture fixture = WebExtensionFixture.register(new FixtureModule());

    assertThat(fixture.pages())
        .containsExactly(
            new WebExtensionFixture.RegisteredPage(
                "overview", "Overview", "Overview page", "apps"));
    assertThat(fixture.routes())
        .extracting(
            WebExtensionFixture.RegisteredRoute::method, WebExtensionFixture.RegisteredRoute::path)
        .containsExactly(tuple("GET", "/overview"), tuple("POST", "/overview/submit"));
    assertThat(fixture.routesFor("overview")).hasSize(2);

    SeaWebResponse get =
        fixture.invoke(
            "GET",
            "/overview",
            new SeaWebRequest(
                "GET", "/overview", Map.of("name", List.of("Ada")), Map.of(), Map.of(), Map.of()));
    assertThat(get.status()).isEqualTo(200);
    assertThat(get.contentType()).isEqualTo("text/html");
    assertThat(get.body()).contains("Hello Ada");

    SeaWebResponse post = fixture.submit("/overview/submit", Map.of("note", List.of("hi")));
    assertThat(post.body()).contains("Saved hi");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> fixture.invoke("GET", "/missing"))
        .withMessage("Module does not register route GET /missing");
  }

  private static final class FixtureModule implements SeaModule {
    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor(MODULE_ID, "1.0.0", "Web fixture", "Web fixture module");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of();
    }

    @Override
    public List<SeaWebExtension> webExtensions() {
      return List.of(new FixtureExtension());
    }
  }

  private static final class FixtureExtension implements SeaWebExtension {
    @Override
    public WebExtensionDescriptor descriptor() {
      return new WebExtensionDescriptor(
          MODULE_ID, "fixture-extension", "Fixture", "Fixture web extension");
    }

    @Override
    public void register(WebExtensionRegistry registry) {
      registry
          .page("overview")
          .title("Overview")
          .description("Overview page")
          .navSection("apps")
          .get(
              "/overview",
              request ->
                  SeaWebResponse.html(
                      "Hello " + request.firstQueryParameter("name").orElse("world")))
          .post(
              "/overview/submit",
              request ->
                  SeaWebResponse.html("Saved " + request.firstFormParameter("note").orElse("")));
    }
  }
}
