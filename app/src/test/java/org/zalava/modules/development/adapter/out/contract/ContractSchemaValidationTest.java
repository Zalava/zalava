package org.zalava.modules.development.adapter.out.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ContractSchemaValidationTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String INPUT_SCHEMA =
      """
            {"type":"object","required":["name","count","enabled","date"],"additionalProperties":false,
             "properties":{"name":{"type":"string","enum":["zalava"]},"count":{"type":"integer","minimum":1,"maximum":3},
             "enabled":{"type":"boolean"},"date":{"type":"string","format":"date"}}}
            """;

  @Test
  void generatesInvalidCasesForRequiredTypesEnumFormatBoundsAndExtraProperties() throws Exception {
    List<tools.jackson.databind.JsonNode> cases =
        ContractSchemaValidation.invalidInputs(
            INPUT_SCHEMA,
            JSON.readTree(
                "{\"name\":\"zalava\",\"count\":2,\"enabled\":true,\"date\":\"2026-08-01\"}"));

    assertThat(cases).hasSizeGreaterThanOrEqualTo(9);
    assertThat(cases)
        .anySatisfy(value -> assertThat(value.has("name")).isFalse())
        .anySatisfy(value -> assertThat(value.path("name").isNumber()).isTrue())
        .anySatisfy(
            value ->
                assertThat(value.path("name").stringValue("")).isEqualTo("__zalava_invalid_enum__"))
        .anySatisfy(
            value ->
                assertThat(value.path("count").isNumber() && value.path("count").asInt() < 1)
                    .isTrue())
        .anySatisfy(
            value ->
                assertThat(value.path("count").isNumber() && value.path("count").asInt() > 3)
                    .isTrue())
        .anySatisfy(value -> assertThat(value.has("zalavaUnexpectedProperty")).isTrue());
  }

  @Test
  void identifiesSmallestUsefulOutputViolationPath() throws Exception {
    assertThatThrownBy(
            () ->
                ContractSchemaValidation.requireValid(
                    "{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"object\",\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"integer\"}}}}}",
                    JSON.readTree("{\"result\":{\"value\":\"wrong\"}}"),
                    "Successful response"))
        .hasMessageContaining("$.result.value");
  }

  @Test
  void requiresStrictRequestedAndExposedInputSchemas() {
    ContractSchemaValidation.requireEquivalentInputSchema(
        "{\"type\":\"object\"}", Map.of("type", "object"), "lookup");

    assertThatThrownBy(
            () ->
                ContractSchemaValidation.requireEquivalentInputSchema(
                    "{\"type\":\"object\",\"required\":[\"name\"]}",
                    Map.of("type", "object"),
                    "lookup"))
        .hasMessageContaining("Requested and exposed input schemas differ");
  }
}
