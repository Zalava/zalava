package org.zalava.modules.development.adapter.out.contract;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.zalava.modules.development.ModuleDevelopmentContract;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers the branch paths of the deterministic acceptance assertion language: every assertion
 * type's failing side, malformed paths and expected values, and the composite evidence helpers.
 */
class AcceptanceAssertionsBranchesTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String RESPONSE =
      "{\"name\":\"sea\",\"count\":2,\"flag\":true,\"items\":[\"a\",\"b\"],"
          + "\"records\":[{\"rank\":1,\"name\":\"z\"},{\"rank\":2,\"name\":\"a\"}],"
          + "\"nested\":{\"value\":5}}";

  static Stream<Arguments> failingAssertions() {
    return Stream.of(
        Arguments.of("$.name", "notEquals", "\"sea\""),
        Arguments.of("$.missing", "exists", "true"),
        Arguments.of("$.name", "type", "\"number\""),
        Arguments.of("$.count", "type", "\"boolean\""),
        Arguments.of("$.flag", "type", "\"integer\""),
        Arguments.of("$.name", "matches", "\"o.*\""),
        Arguments.of("$.name", "between", "{\"min\":5,\"max\":9}"),
        Arguments.of("$.missing", "between", "{\"min\":1,\"max\":3}"),
        Arguments.of("$.name", "between", "\"not-an-object\""),
        Arguments.of("$.items", "minItems", "3"),
        Arguments.of("$.name", "minItems", "1"),
        Arguments.of("$.items", "maxItems", "1"),
        Arguments.of("$.items", "contains", "\"c\""),
        Arguments.of("$.records", "contains", "\"a\""),
        Arguments.of("$.name", "unique", "true"),
        Arguments.of("$.name", "orderedBy", "\"$.rank\""),
        Arguments.of("$.records", "orderedBy", "\"$.name\""),
        Arguments.of("$.records", "orderedBy", "\"$.missing\""));
  }

  @ParameterizedTest
  @MethodSource("failingAssertions")
  void failingAssertionsThrowWithEvidence(String path, String type, String expected)
      throws Exception {
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion(path, type, expected), JSON.readTree(RESPONSE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Acceptance assertion failed at " + path)
        .hasMessageContaining("expected")
        .hasMessageContaining("observed");
  }

  static Stream<Arguments> passingAssertions() {
    return Stream.of(
        Arguments.of(
            Named.of(
                "numeric orderedBy",
                (Runnable) () -> satisfied("$.records", "orderedBy", "\"$.rank\""))),
        Arguments.of(
            Named.of(
                "open between bounds",
                (Runnable) () -> satisfied("$.nested.value", "between", "{}"))),
        Arguments.of(
            Named.of(
                "inclusive minimum",
                (Runnable) () -> satisfied("$.nested.value", "between", "{\"min\":5}"))),
        Arguments.of(
            Named.of(
                "inclusive maximum",
                (Runnable) () -> satisfied("$.nested.value", "between", "{\"max\":5}"))),
        Arguments.of(
            Named.of(
                "array element path", (Runnable) () -> satisfied("$.items[0]", "equals", "\"a\""))),
        Arguments.of(
            Named.of(
                "nested index path",
                (Runnable) () -> satisfied("$.records[1].rank", "equals", "2"))),
        Arguments.of(
            Named.of("text contains", (Runnable) () -> satisfied("$.name", "contains", "\"ea\""))),
        Arguments.of(
            Named.of(
                "array contains object",
                (Runnable)
                    () -> satisfied("$.records", "contains", "{\"rank\":1,\"name\":\"z\"}"))));
  }

  @ParameterizedTest
  @MethodSource("passingAssertions")
  void passingAssertionsAreSatisfied(Runnable assertion) {
    assertion.run();
  }

  private static void satisfied(String path, String type, String expected) {
    assertThatCode(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion(path, type, expected), JSON.readTree(RESPONSE)))
        .doesNotThrowAnyException();
  }

  @Test
  void betweenRejectsBelowTheInclusiveMinimum() throws Exception {
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion("$.nested.value", "between", "{\"min\":6}"), JSON.readTree(RESPONSE)))
        .hasMessageContaining("Acceptance assertion failed at $.nested.value");
  }

  @Test
  void uniqueRejectsDuplicateArrayElements() throws Exception {
    String duplicated = "{\"items\":[{\"rank\":1},{\"rank\":1}]}";
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion("$.items", "unique", "true"), JSON.readTree(duplicated)))
        .hasMessageContaining("Acceptance assertion failed at $.items");
  }

  @Test
  void equalsRejectsAMismatchedValueAndReportsBothSides() throws Exception {
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion("$.name", "json_equal", "\"ocean\""), JSON.readTree(RESPONSE)))
        .hasMessageContaining("expected \"ocean\"")
        .hasMessageContaining("observed \"sea\"");
  }

  @Test
  void rejectsUnsupportedAssertionTypes() throws Exception {
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion("$.name", "startsWith", "\"s\""), JSON.readTree(RESPONSE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported acceptance assertion type: startsWith");
  }

  @Test
  void rejectsInvalidExpectedJson() throws Exception {
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion("$.name", "equals", "not-json"), JSON.readTree(RESPONSE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expected value is invalid JSON: $.name");
  }

  @Test
  void rejectsPathsThatAreNotAbsoluteFieldPointers() throws Exception {
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion("name", "exists", "true"), JSON.readTree(RESPONSE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("path must start with $");
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion("$name", "exists", "true"), JSON.readTree(RESPONSE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("$.field notation");
    assertThatThrownBy(() -> assertion(null, "exists", "true"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("assertion.path must not be blank");
  }

  private static ModuleDevelopmentContract.ResponseAssertion assertion(
      String path, String type, String expected) {
    return new ModuleDevelopmentContract.ResponseAssertion(path, type, expected);
  }
}
