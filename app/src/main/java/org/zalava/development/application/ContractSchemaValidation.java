package org.zalava.development.application;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** JSON Schema evidence checks for the authoritative development contract. */
final class ContractSchemaValidation {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final SchemaRegistry SCHEMAS =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  private ContractSchemaValidation() {}

  static JsonNode schema(String source, String description) {
    try {
      return JSON.readTree(source);
    } catch (Exception ex) {
      throw new IllegalStateException(description + " is not valid JSON", ex);
    }
  }

  static void requireEquivalentInputSchema(
      String requested, Map<String, Object> exposed, String toolName) {
    JsonNode requestedSchema = schema(requested, "Requested input schema for " + toolName);
    JsonNode exposedSchema = JSON.valueToTree(exposed);
    if (!requestedSchema.equals(exposedSchema)) {
      throw new IllegalStateException(
          "Requested and exposed input schemas differ for tool " + toolName);
    }
  }

  static void requireValid(String schemaSource, JsonNode value, String subject) {
    Schema schema = SCHEMAS.getSchema(schema(schemaSource, subject + " schema"));
    var violations = schema.validate(value);
    if (!violations.isEmpty()) {
      var violation =
          violations.stream()
              .min(Comparator.comparing(message -> message.getInstanceLocation().toString()))
              .orElseThrow();
      String path = violation.getInstanceLocation().toString();
      throw new IllegalStateException(
          subject
              + " violates schema at "
              + (path.isBlank() ? "$" : "$" + path.replace('/', '.'))
              + ": "
              + violation.getMessage());
    }
  }

  static List<JsonNode> invalidInputs(String schemaSource, JsonNode validInput) {
    JsonNode schema = schema(schemaSource, "Requested input schema");
    if (!schema.path("type").asText().equals("object") || !validInput.isObject()) return List.of();
    var probes = new java.util.ArrayList<JsonNode>();
    for (JsonNode required : schema.path("required")) {
      ObjectNode missing = (ObjectNode) validInput.deepCopy();
      missing.remove(required.asText());
      addIfInvalid(schemaSource, missing, probes);
    }
    schema
        .path("properties")
        .properties()
        .forEach(
            property -> {
              String name = property.getKey();
              JsonNode definition = property.getValue();
              for (JsonNode invalid : invalidValues(definition)) {
                ObjectNode probe = (ObjectNode) validInput.deepCopy();
                probe.set(name, invalid);
                addIfInvalid(schemaSource, probe, probes);
              }
            });
    if (!schema.path("additionalProperties").asBoolean(true)) {
      ObjectNode probe = (ObjectNode) validInput.deepCopy();
      probe.put("seaUnexpectedProperty", true);
      addIfInvalid(schemaSource, probe, probes);
    }
    return List.copyOf(probes);
  }

  private static List<JsonNode> invalidValues(JsonNode definition) {
    var values = new java.util.ArrayList<JsonNode>();
    switch (definition.path("type").asText()) {
      case "string" -> values.add(JSON.getNodeFactory().numberNode(1));
      case "integer", "number" -> values.add(JSON.getNodeFactory().textNode("not-a-number"));
      case "boolean" -> values.add(JSON.getNodeFactory().textNode("not-a-boolean"));
      case "array" -> values.add(JSON.getNodeFactory().textNode("not-an-array"));
      case "object" -> values.add(JSON.getNodeFactory().textNode("not-an-object"));
      default -> {}
    }
    if (definition.has("enum")) values.add(JSON.getNodeFactory().textNode("__sea_invalid_enum__"));
    if (definition.has("format"))
      values.add(JSON.getNodeFactory().textNode("not-a-" + definition.path("format").asText()));
    if (definition.has("minimum"))
      values.add(
          JSON.getNodeFactory()
              .numberNode(
                  definition.get("minimum").decimalValue().subtract(java.math.BigDecimal.ONE)));
    if (definition.has("maximum"))
      values.add(
          JSON.getNodeFactory()
              .numberNode(definition.get("maximum").decimalValue().add(java.math.BigDecimal.ONE)));
    return List.copyOf(values);
  }

  private static void addIfInvalid(String schemaSource, JsonNode candidate, List<JsonNode> probes) {
    try {
      requireValid(schemaSource, candidate, "Generated invalid input");
    } catch (IllegalStateException ignored) {
      probes.add(candidate);
    }
  }
}
