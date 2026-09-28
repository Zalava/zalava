package org.zalava.development.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.zalava.development.ModuleDevelopmentContract;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

class AcceptanceAssertionsTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String RESPONSE =
      """
            {"name":"sea","count":2,"items":["a","b"],"records":[{"rank":1},{"rank":2}]}""";

  @ParameterizedTest
  @CsvSource({
    "$.name,equals,\"sea\"",
    "$.name,notEquals,\"ocean\"",
    "$.name,exists,true",
    "$.name,type,\"string\"",
    "$.name,matches,\"s.*\"",
    "$.count,between,{\"min\":1;\"max\":3}",
    "$.items,minItems,2",
    "$.items,maxItems,2",
    "$.items,contains,\"a\"",
    "$.items,unique,true",
    "$.records,orderedBy,\"$.rank\""
  })
  void supportsEachBoundedAssertion(String path, String type, String expected) throws Exception {
    assertThatCode(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion(path, type, expected.replace(';', ',')), JSON.readTree(RESPONSE)))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({"$.name,equals,\"ocean\"", "$.items,minItems,3", "$.records,orderedBy,\"$.missing\""})
  void includesExpectedAndActualEvidenceForFailures(String path, String type, String expected)
      throws Exception {
    assertThatThrownBy(
            () ->
                AcceptanceAssertions.requireSatisfied(
                    assertion(path, type, expected), JSON.readTree(RESPONSE)))
        .hasMessageContaining("Acceptance assertion failed at " + path, "expected", "observed");
  }

  private static ModuleDevelopmentContract.ResponseAssertion assertion(
      String path, String type, String expected) {
    return new ModuleDevelopmentContract.ResponseAssertion(path, type, expected);
  }
}
