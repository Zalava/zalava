package org.zalava.web.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.zalava.api.ModuleConfigurationDescriptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Converts the supported, scalar subset of a module JSON schema into a safe HTML form model. */
public final class ModuleConfigurationForm {

  private static final ObjectMapper JSON = new ObjectMapper();

  private ModuleConfigurationForm() {}

  public static List<Field> fields(
      ModuleConfigurationDescriptor descriptor, Map<String, Object> values) {
    List<Field> fields = new ArrayList<>();
    addFields(fields, "", descriptor.jsonSchema(), values);
    return List.copyOf(fields);
  }

  public static Map<String, Object> document(List<Field> fields, Map<String, String> submitted) {
    Map<String, Object> document = new LinkedHashMap<>();
    for (Field field : fields) {
      String value = submitted.get(field.name());
      if (value == null && field.required() && field.type().equals("boolean")) {
        value = "false";
      }
      if (value == null || value.isBlank()) {
        if (field.required()) {
          throw new IllegalArgumentException(field.label() + " is required.");
        }
        continue;
      }
      put(document, field.path(), typedValue(field, value));
    }
    return document;
  }

  private static void addFields(
      List<Field> fields, String path, Map<String, Object> schema, Map<String, Object> values) {
    Map<String, Object> properties = map(schema.get("properties"));
    List<String> requiredProperties = strings(schema.get("required"));
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String name = entry.getKey();
      Map<String, Object> property = map(entry.getValue());
      String propertyPath = path.isEmpty() ? name : path + "." + name;
      Object value = values.get(name);
      if ("object".equals(property.get("type")) || property.containsKey("properties")) {
        addFields(fields, propertyPath, property, map(value));
        continue;
      }
      fields.add(
          new Field(
              propertyPath,
              label(propertyPath, property),
              type(property),
              requiredProperties.contains(name),
              secretReference(name, property),
              displayValue(type(property), value),
              string(property.get("description"))));
    }
  }

  private static Object typedValue(Field field, String value) {
    try {
      return switch (field.type()) {
        case "number" -> new BigDecimal(value);
        case "integer" -> Long.parseLong(value);
        case "boolean" -> Boolean.parseBoolean(value);
        case "json" -> JSON.readValue(value, Object.class);
        default -> value;
      };
    } catch (JacksonException exception) {
      throw new IllegalArgumentException(field.label() + " must be valid JSON.");
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, entry) -> result.put(String.valueOf(key), entry));
    return result;
  }

  private static List<String> strings(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  @SuppressWarnings("unchecked")
  private static void put(Map<String, Object> document, String path, Object value) {
    String[] segments = path.split("\\.");
    Map<String, Object> current = document;
    for (int index = 0; index < segments.length - 1; index++) {
      Object child =
          current.computeIfAbsent(segments[index], ignored -> new LinkedHashMap<String, Object>());
      current = (Map<String, Object>) child;
    }
    current.put(segments[segments.length - 1], value);
  }

  private static String type(Map<String, Object> schema) {
    String type = string(schema.get("type"));
    if ("array".equals(type) || "object".equals(type)) {
      return "json";
    }
    return List.of("number", "integer", "boolean").contains(type) ? type : "string";
  }

  private static String displayValue(String type, Object value) {
    if (value == null) {
      return "";
    }
    if (!"json".equals(type)) {
      return String.valueOf(value);
    }
    try {
      return JSON.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Unable to render structured configuration value.");
    }
  }

  private static boolean secretReference(String name, Map<String, Object> schema) {
    return name.endsWith("Ref") || Boolean.TRUE.equals(schema.get("x-secret-reference"));
  }

  private static String label(String path, Map<String, Object> schema) {
    String title = string(schema.get("title"));
    return title == null ? path : title;
  }

  private static String string(Object value) {
    return value instanceof String string ? string : null;
  }

  public record Field(
      String path,
      String label,
      String type,
      boolean required,
      boolean secretReference,
      String value,
      String description) {
    public String name() {
      return "configuration." + path;
    }

    public String replacementSecretName() {
      return "replacement-secret." + path;
    }
  }
}
