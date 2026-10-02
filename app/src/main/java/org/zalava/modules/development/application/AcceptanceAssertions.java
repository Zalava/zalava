package org.zalava.modules.development.application;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.zalava.modules.development.ModuleDevelopmentContract;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Deterministic, deliberately small assertion language for SEA-owned acceptance scenarios. */
final class AcceptanceAssertions {
  private static final ObjectMapper JSON = new ObjectMapper();

  private AcceptanceAssertions() {}

  static void requireSatisfied(
      ModuleDevelopmentContract.ResponseAssertion assertion, JsonNode response) {
    JsonNode actual = response.at(jsonPointer(assertion.path()));
    JsonNode expected = parseExpected(assertion);
    boolean satisfied =
        switch (assertion.type()) {
          case "equals", "json_equal" -> expected.equals(actual);
          case "notEquals" -> !expected.equals(actual);
          case "exists" -> !actual.isMissingNode() && !actual.isNull();
          case "type" -> actualType(actual).equals(expected.stringValue(""));
          case "matches" ->
              actual.isString()
                  && Pattern.compile(expected.stringValue(""))
                      .matcher(actual.stringValue(""))
                      .matches();
          case "between" -> within(actual, expected);
          case "minItems" -> actual.isArray() && actual.size() >= expected.asInt();
          case "maxItems" -> actual.isArray() && actual.size() <= expected.asInt();
          case "contains" -> contains(actual, expected);
          case "unique" -> unique(actual);
          case "orderedBy" -> orderedBy(actual, expected.stringValue(""));
          default ->
              throw new IllegalStateException(
                  "Unsupported acceptance assertion type: " + assertion.type());
        };
    if (!satisfied)
      throw new IllegalStateException(
          "Acceptance assertion failed at "
              + assertion.path()
              + ": expected "
              + expected
              + " but observed "
              + actual);
  }

  private static JsonNode parseExpected(ModuleDevelopmentContract.ResponseAssertion assertion) {
    try {
      return JSON.readTree(assertion.expectedValueJson());
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Acceptance assertion expected value is invalid JSON: " + assertion.path(), ex);
    }
  }

  private static boolean within(JsonNode actual, JsonNode expected) {
    if (!actual.isNumber() || !expected.isObject()) return false;
    BigDecimal value = actual.decimalValue();
    JsonNode minimum = expected.path("min");
    JsonNode maximum = expected.path("max");
    return (!minimum.isNumber() || value.compareTo(minimum.decimalValue()) >= 0)
        && (!maximum.isNumber() || value.compareTo(maximum.decimalValue()) <= 0);
  }

  private static boolean contains(JsonNode actual, JsonNode expected) {
    if (actual.isArray()) {
      for (JsonNode value : actual) if (value.equals(expected)) return true;
      return false;
    }
    return actual.isString()
        && expected.isString()
        && actual.stringValue("").contains(expected.stringValue(""));
  }

  private static boolean unique(JsonNode actual) {
    if (!actual.isArray()) return false;
    HashSet<JsonNode> seen = new HashSet<>();
    for (JsonNode value : actual) if (!seen.add(value)) return false;
    return true;
  }

  private static boolean orderedBy(JsonNode actual, String propertyPath) {
    if (!actual.isArray()) return false;
    JsonNode previous = null;
    for (JsonNode value : actual) {
      JsonNode current = propertyPath.equals("$") ? value : value.at(jsonPointer(propertyPath));
      if (current.isMissingNode()
          || current.isObject()
          || current.isArray()
          || previous != null && compare(previous, current) > 0) return false;
      previous = current;
    }
    return true;
  }

  private static int compare(JsonNode left, JsonNode right) {
    if (left.isNumber() && right.isNumber())
      return left.decimalValue().compareTo(right.decimalValue());
    return left.stringValue("").compareTo(right.stringValue(""));
  }

  private static String actualType(JsonNode actual) {
    if (actual.isMissingNode() || actual.isNull()) return "null";
    if (actual.isObject()) return "object";
    if (actual.isArray()) return "array";
    if (actual.isString()) return "string";
    if (actual.isBoolean()) return "boolean";
    if (actual.isIntegralNumber()) return "integer";
    if (actual.isNumber()) return "number";
    return actual.getNodeType().name().toLowerCase(java.util.Locale.ROOT);
  }

  private static String jsonPointer(String path) {
    if (path == null || !path.startsWith("$"))
      throw new IllegalStateException("Acceptance assertion path must start with $");
    if (path.equals("$")) return "";
    if (!path.startsWith("$."))
      throw new IllegalStateException("Acceptance assertion path must use $.field notation");
    return "/" + path.substring(2).replace("[", "/").replace("]", "").replace(".", "/");
  }
}
