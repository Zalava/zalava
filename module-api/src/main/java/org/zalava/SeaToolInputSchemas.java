package org.zalava;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SeaToolInputSchemas {

  private SeaToolInputSchemas() {}

  public static Map<String, Object> object(Map<String, Object> properties, String... required) {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.copyOf(properties),
        "required",
        List.copyOf(Arrays.asList(required)),
        "additionalProperties",
        false);
  }

  public static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  public static Map<String, Object> integer() {
    return Map.of("type", "integer");
  }

  public static Map<String, Object> bool() {
    return Map.of("type", "boolean");
  }

  public static Map<String, Object> stringArray() {
    return Map.of("type", "array", "items", string());
  }

  public static Map<String, Object> immutable(Map<String, Object> values) {
    Objects.requireNonNull(values, "inputSchema");
    return values.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> immutableValue(entry.getValue())));
  }

  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return map.entrySet().stream()
          .collect(
              Collectors.toUnmodifiableMap(
                  entry -> Objects.toString(entry.getKey()),
                  entry -> immutableValue(entry.getValue())));
    }
    if (value instanceof List<?> list) {
      return list.stream().map(SeaToolInputSchemas::immutableValue).toList();
    }
    return Objects.requireNonNull(value, "inputSchema value");
  }
}
