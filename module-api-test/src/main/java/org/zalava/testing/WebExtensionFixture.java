package org.zalava.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.zalava.ZalavaModule;
import org.zalava.web.WebExtensionRegistry;
import org.zalava.web.WebPageRegistration;
import org.zalava.web.ZalavaWebExtension;
import org.zalava.web.ZalavaWebHandler;
import org.zalava.web.ZalavaWebRequest;
import org.zalava.web.ZalavaWebResponse;

/**
 * Registers a module's web extensions and invokes their handlers with synthetic requests. It
 * asserts the exact routes a module declares and the responses its handlers return for a supplied
 * request; SEA's route parsing, security and HTTP chrome remain host-owned and are not
 * reimplemented.
 */
public final class WebExtensionFixture {
  private final List<RegisteredPage> pages;
  private final List<RegisteredRoute> routes;

  private WebExtensionFixture(List<RegisteredPage> pages, List<RegisteredRoute> routes) {
    this.pages = pages;
    this.routes = routes;
  }

  public static WebExtensionFixture register(ZalavaModule module) {
    Objects.requireNonNull(module, "module");
    Registry registry = new Registry();
    for (ZalavaWebExtension extension : module.webExtensions()) {
      Objects.requireNonNull(extension, "web extension");
      extension.register(registry);
    }
    return new WebExtensionFixture(registry.pages(), List.copyOf(registry.routes));
  }

  public List<RegisteredPage> pages() {
    return pages;
  }

  public List<RegisteredRoute> routes() {
    return routes;
  }

  public List<RegisteredRoute> routesFor(String pageId) {
    Objects.requireNonNull(pageId, "pageId");
    return routes.stream().filter(route -> route.pageId().equals(pageId)).toList();
  }

  public ZalavaWebResponse invoke(String method, String path, ZalavaWebRequest request) {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(path, "path");
    RegisteredRoute route =
        routes.stream()
            .filter(
                candidate ->
                    candidate.method().equalsIgnoreCase(method) && candidate.path().equals(path))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Module does not register route " + method + " " + path));
    return route.handler().handle(request);
  }

  public ZalavaWebResponse invoke(String method, String path) {
    return invoke(
        method, path, new ZalavaWebRequest(method, path, Map.of(), Map.of(), Map.of(), Map.of()));
  }

  public ZalavaWebResponse submit(String path, Map<String, List<String>> formParameters) {
    return invoke(
        "POST",
        path,
        new ZalavaWebRequest("POST", path, Map.of(), formParameters, Map.of(), Map.of()));
  }

  public record RegisteredPage(
      String pageId, String title, String description, String navSection) {}

  public record RegisteredRoute(
      String pageId, String method, String path, ZalavaWebHandler handler) {}

  private static final class Registry implements WebExtensionRegistry {
    private final Map<String, PageState> pages = new LinkedHashMap<>();
    private final List<RegisteredRoute> routes = new ArrayList<>();

    @Override
    public WebPageRegistration page(String pageId) {
      PageState state = pages.computeIfAbsent(pageId, PageState::new);
      return new Registration(state);
    }

    List<RegisteredPage> pages() {
      return pages.values().stream().map(PageState::snapshot).toList();
    }

    private final class Registration implements WebPageRegistration {
      private final PageState state;

      Registration(PageState state) {
        this.state = state;
      }

      @Override
      public WebPageRegistration title(String title) {
        state.title = title;
        return this;
      }

      @Override
      public WebPageRegistration description(String description) {
        state.description = description;
        return this;
      }

      @Override
      public WebPageRegistration navSection(String navSection) {
        state.navSection = navSection;
        return this;
      }

      @Override
      public WebPageRegistration get(String path, ZalavaWebHandler handler) {
        routes.add(new RegisteredRoute(state.pageId, "GET", path, handler));
        return this;
      }

      @Override
      public WebPageRegistration post(String path, ZalavaWebHandler handler) {
        routes.add(new RegisteredRoute(state.pageId, "POST", path, handler));
        return this;
      }
    }
  }

  private static final class PageState {
    private final String pageId;
    private String title = "";
    private String description = "";
    private String navSection = "apps";

    PageState(String pageId) {
      this.pageId = pageId;
    }

    RegisteredPage snapshot() {
      return new RegisteredPage(pageId, title, description, navSection);
    }
  }
}
