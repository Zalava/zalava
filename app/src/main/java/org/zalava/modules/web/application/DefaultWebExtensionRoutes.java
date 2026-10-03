package org.zalava.modules.web.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.extensions.web.WebExtensionDescriptor;
import org.zalava.api.extensions.web.WebExtensionRegistry;
import org.zalava.api.extensions.web.WebPageRegistration;
import org.zalava.api.extensions.web.ZalavaWebExtension;
import org.zalava.api.extensions.web.ZalavaWebHandler;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;
import org.zalava.modules.web.application.port.out.WebExtensionModuleCatalog;
import org.zalava.modules.web.domain.RoutePattern;

public final class DefaultWebExtensionRoutes implements WebExtensionRoutes {

  private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-z0-9][a-z0-9-]*");

  private final WebExtensionModuleCatalog moduleCatalog;

  public DefaultWebExtensionRoutes(WebExtensionModuleCatalog moduleCatalog) {
    this.moduleCatalog = moduleCatalog;
  }

  @Override
  public List<RegisteredWebPage> pages() {
    return moduleCatalog.modules().stream()
        .flatMap(module -> pagesFor(module).stream())
        .sorted()
        .toList();
  }

  @Override
  public Optional<RouteInvocation> resolve(
      String method, String moduleId, String pageId, String path) {
    String normalizedPath = RoutePattern.normalize(path);
    String normalizedMethod = method.toUpperCase(Locale.ROOT);
    return pages().stream()
        .filter(page -> page.moduleId().equals(moduleId) && page.pageId().equals(pageId))
        .flatMap(
            page ->
                page.routes().stream()
                    .filter(route -> route.method().equals(normalizedMethod))
                    .flatMap(
                        route ->
                            route
                                .match(normalizedPath)
                                .map(
                                    pathVariables ->
                                        new RouteInvocation(
                                            page, route, normalizedPath, pathVariables))
                                .stream()))
        .findFirst();
  }

  private static List<RegisteredWebPage> pagesFor(ZalavaModule module) {
    ModuleDescriptor moduleDescriptor = module.descriptor();
    requireSafeSegment(moduleDescriptor.moduleId(), "module id");
    List<RegisteredWebPage> registered = new ArrayList<>();
    for (ZalavaWebExtension extension : module.webExtensions()) {
      WebExtensionDescriptor descriptor = requireDescriptor(moduleDescriptor, extension);
      ExtensionRegistry registry = new ExtensionRegistry(moduleDescriptor, descriptor);
      extension.register(registry);
      registered.addAll(registry.pages());
    }
    return List.copyOf(registered);
  }

  private static WebExtensionDescriptor requireDescriptor(
      ModuleDescriptor module, ZalavaWebExtension extension) {
    if (extension == null) {
      throw new IllegalArgumentException("Zalava web extension must not be null");
    }
    WebExtensionDescriptor descriptor = extension.descriptor();
    if (descriptor == null) {
      throw new IllegalArgumentException("Zalava web extension descriptor must not be null");
    }
    requireText(descriptor.moduleId(), "web extension module id");
    if (!module.moduleId().equals(descriptor.moduleId())) {
      throw new IllegalArgumentException(
          "Zalava web extension "
              + descriptor.extensionId()
              + " belongs to module "
              + descriptor.moduleId()
              + " but owning module is "
              + module.moduleId());
    }
    requireSafeSegment(descriptor.extensionId(), "web extension id");
    requireText(descriptor.displayName(), "web extension display name");
    requireText(descriptor.description(), "web extension description");
    return descriptor;
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Zalava " + field + " must not be blank");
    }
  }

  private static void requireSafeSegment(String value, String field) {
    requireText(value, field);
    if (!SAFE_SEGMENT.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Zalava " + field + " must be a lowercase route segment: " + value);
    }
  }

  private static final class ExtensionRegistry implements WebExtensionRegistry {

    private final ModuleDescriptor module;
    private final WebExtensionDescriptor extension;
    private final List<PageRegistration> pages = new ArrayList<>();

    private ExtensionRegistry(ModuleDescriptor module, WebExtensionDescriptor extension) {
      this.module = module;
      this.extension = extension;
    }

    @Override
    public WebPageRegistration page(String pageId) {
      requireSafeSegment(pageId, "web extension page id");
      PageRegistration registration = new PageRegistration(module, extension, pageId);
      pages.add(registration);
      return registration;
    }

    List<RegisteredWebPage> pages() {
      return pages.stream().map(PageRegistration::build).toList();
    }
  }

  private static final class PageRegistration implements WebPageRegistration {

    private final ModuleDescriptor module;
    private final WebExtensionDescriptor extension;
    private final String pageId;
    private final List<RegisteredWebRoute> routes = new ArrayList<>();
    private String title;
    private String description;
    private String navSection = "apps";

    private PageRegistration(
        ModuleDescriptor module, WebExtensionDescriptor extension, String pageId) {
      this.module = module;
      this.extension = extension;
      this.pageId = pageId;
      this.title = extension.displayName();
      this.description = extension.description();
    }

    @Override
    public WebPageRegistration title(String title) {
      requireText(title, "web extension page title");
      this.title = title;
      return this;
    }

    @Override
    public WebPageRegistration description(String description) {
      requireText(description, "web extension page description");
      this.description = description;
      return this;
    }

    @Override
    public WebPageRegistration navSection(String navSection) {
      requireSafeSegment(navSection, "web extension nav section");
      this.navSection = navSection;
      return this;
    }

    @Override
    public WebPageRegistration get(String path, ZalavaWebHandler handler) {
      return route("GET", path, handler);
    }

    @Override
    public WebPageRegistration post(String path, ZalavaWebHandler handler) {
      return route("POST", path, handler);
    }

    private WebPageRegistration route(String method, String path, ZalavaWebHandler handler) {
      if (handler == null) {
        throw new IllegalArgumentException("Zalava web extension route handler must not be null");
      }
      routes.add(new RegisteredWebRoute(method, RoutePattern.parse(path), handler));
      return this;
    }

    private RegisteredWebPage build() {
      if (routes.isEmpty()) {
        throw new IllegalArgumentException(
            "Zalava web extension page " + pageId + " must register at least one route");
      }
      return new RegisteredWebPage(
          module.moduleId(),
          module.displayName(),
          extension.extensionId(),
          pageId,
          title,
          description,
          navSection,
          List.copyOf(routes));
    }
  }
}
