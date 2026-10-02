package org.zalava.api.extensions.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ZalavaWebRequest(
    String method,
    String path,
    Map<String, List<String>> queryParameters,
    Map<String, List<String>> formParameters,
    Map<String, String> pathVariables,
    Map<String, String> attributes) {

  public ZalavaWebRequest {
    queryParameters = copyMultiMap(queryParameters);
    formParameters = copyMultiMap(formParameters);
    pathVariables = pathVariables == null ? Map.of() : Map.copyOf(pathVariables);
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }

  public Optional<String> firstQueryParameter(String name) {
    return first(queryParameters, name);
  }

  public Optional<String> firstFormParameter(String name) {
    return first(formParameters, name);
  }

  private static Optional<String> first(Map<String, List<String>> parameters, String name) {
    List<String> values = parameters.get(name);
    if (values == null || values.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(values.getFirst());
  }

  private static Map<String, List<String>> copyMultiMap(Map<String, List<String>> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return Map.of();
    }
    return parameters.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
  }
}
