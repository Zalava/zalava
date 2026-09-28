package org.zalava.web.application;

import java.util.Map;
import java.util.Optional;
import org.zalava.web.SeaWebHandler;
import org.zalava.web.domain.RoutePattern;

public record RegisteredWebRoute(String method, RoutePattern pattern, SeaWebHandler handler) {

  Optional<Map<String, String>> match(String path) {
    return pattern.match(path);
  }
}
