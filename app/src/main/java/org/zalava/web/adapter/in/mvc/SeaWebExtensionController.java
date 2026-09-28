package org.zalava.web.adapter.in.mvc;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;
import org.zalava.web.SeaWebRequest;
import org.zalava.web.SeaWebResponse;
import org.zalava.web.application.RouteInvocation;
import org.zalava.web.application.port.in.WebExtensionRoutes;

@Controller
public final class SeaWebExtensionController {

  private final WebExtensionRoutes routes;

  public SeaWebExtensionController(WebExtensionRoutes routes) {
    this.routes = routes;
  }

  @GetMapping("/apps")
  public String apps(Model model) {
    model.addAttribute("pages", routes.pages());
    return "ui/apps";
  }

  @RequestMapping(
      value = {"/apps/{moduleId}/{pageId}", "/apps/{moduleId}/{pageId}/**"},
      method = {RequestMethod.GET, RequestMethod.POST})
  public Object dispatch(
      @PathVariable String moduleId,
      @PathVariable String pageId,
      HttpServletRequest servletRequest) {
    String path = extensionPath(servletRequest, moduleId, pageId);
    RouteInvocation invocation =
        routes
            .resolve(servletRequest.getMethod(), moduleId, pageId, path)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "SEA module app route not found"));
    SeaWebResponse response =
        invocation
            .route()
            .handler()
            .handle(
                new SeaWebRequest(
                    servletRequest.getMethod(),
                    invocation.path(),
                    queryParameters(servletRequest),
                    formParameters(servletRequest),
                    invocation.pathVariables(),
                    Map.of(
                        "moduleId",
                        moduleId,
                        "pageId",
                        pageId,
                        "extensionId",
                        invocation.page().extensionId())));
    if (!response.contentType().startsWith("text/html")) {
      return ResponseEntity.status(response.status())
          .contentType(MediaType.parseMediaType(response.contentType()))
          .body(response.body());
    }
    ModelAndView view = new ModelAndView("ui/module-app");
    view.setStatus(HttpStatus.valueOf(response.status()));
    view.addObject("title", invocation.page().title());
    view.addObject("content", response.body());
    return view;
  }

  private static String extensionPath(HttpServletRequest request, String moduleId, String pageId) {
    String requestPath = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (!contextPath.isBlank() && requestPath.startsWith(contextPath)) {
      requestPath = requestPath.substring(contextPath.length());
    }
    String prefix = "/apps/" + moduleId + "/" + pageId;
    return requestPath.equals(prefix) || requestPath.equals(prefix + "/")
        ? "/"
        : requestPath.substring(prefix.length());
  }

  private static Map<String, List<String>> queryParameters(HttpServletRequest request) {
    String query = request.getQueryString();
    if (query == null || query.isBlank()) return Map.of();
    MultiValueMap<String, String> values =
        UriComponentsBuilder.fromUri(URI.create("/?" + query)).build().getQueryParams();
    return values.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
  }

  private static Map<String, List<String>> formParameters(HttpServletRequest request) {
    return request.getParameterMap().entrySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));
  }
}
