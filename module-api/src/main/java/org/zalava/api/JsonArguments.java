package org.zalava.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable JSON-object values without a JSON-library dependency. */
public final class JsonArguments {
  private JsonArguments() {}

  public static Map<String, Object> immutable(Map<String, ?> arguments) {
    if (arguments == null) throw new IllegalArgumentException("JSON arguments must be an object");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) copy(arguments, new IdentityHashMap<>());
    return result;
  }

  private static Object copy(Object value, IdentityHashMap<Object, Boolean> parents) {
    if (value == null || value instanceof String || value instanceof Boolean) return value;
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long
        || value instanceof BigInteger
        || value instanceof BigDecimal) return value;
    if (value instanceof Float number && Float.isFinite(number)) return value;
    if (value instanceof Double number && Double.isFinite(number)) return value;
    if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
      throw new IllegalArgumentException(
          "Unsupported JSON argument value: " + value.getClass().getName());
    }
    if (parents.put(value, Boolean.TRUE) != null) {
      throw new IllegalArgumentException("JSON argument values must not contain cycles");
    }
    try {
      if (value instanceof Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach(
            (key, child) -> {
              if (!(key instanceof String text))
                throw new IllegalArgumentException("JSON object keys must be strings");
              result.put(text, copy(child, parents));
            });
        return Collections.unmodifiableMap(result);
      }
      List<Object> result = new ArrayList<>();
      for (Object child : (List<?>) value) result.add(copy(child, parents));
      return Collections.unmodifiableList(result);
    } finally {
      parents.remove(value);
    }
  }
}
