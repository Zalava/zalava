package org.zalava.modules.web.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record RoutePattern(String source, List<String> segments) {

  public static RoutePattern parse(String path) {
    String normalized = normalize(path);
    if (normalized.contains("//") || normalized.contains("..")) {
      throw new IllegalArgumentException("SEA web extension route path is invalid: " + path);
    }
    return new RoutePattern(normalized, segments(normalized));
  }

  public Optional<Map<String, String>> match(String path) {
    List<String> actual = segments(normalize(path));
    if (actual.size() != segments.size()) {
      return Optional.empty();
    }
    Map<String, String> variables = new java.util.LinkedHashMap<>();
    for (int index = 0; index < segments.size(); index++) {
      String expected = segments.get(index);
      String value = actual.get(index);
      if (expected.startsWith("{") && expected.endsWith("}")) {
        variables.put(expected.substring(1, expected.length() - 1), value);
      } else if (!expected.equals(value)) {
        return Optional.empty();
      }
    }
    return Optional.of(Map.copyOf(variables));
  }

  public static String normalize(String path) {
    if (path == null || path.isBlank() || "/".equals(path)) {
      return "/";
    }
    String normalized = path.startsWith("/") ? path : "/" + path;
    return normalized.length() > 1 && normalized.endsWith("/")
        ? normalized.substring(0, normalized.length() - 1)
        : normalized;
  }

  private static List<String> segments(String path) {
    return "/".equals(path) ? List.of() : List.of(path.substring(1).split("/"));
  }
}
