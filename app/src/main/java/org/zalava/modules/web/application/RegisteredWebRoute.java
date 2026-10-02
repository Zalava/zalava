package org.zalava.modules.web.application;

import java.util.Map;
import java.util.Optional;
import org.zalava.api.extensions.web.ZalavaWebHandler;
import org.zalava.modules.web.domain.RoutePattern;

public record RegisteredWebRoute(String method, RoutePattern pattern, ZalavaWebHandler handler) {

  Optional<Map<String, String>> match(String path) {
    return pattern.match(path);
  }
}
