package org.zalava.capabilities.operation.adapter.out.json;

import java.util.Map;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.capabilities.operation.application.port.out.ToolArgumentDecoder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class JacksonToolArgumentDecoder implements ToolArgumentDecoder {
  private static final ObjectMapper JSON =
      JsonMapper.builder()
          .enable(
              DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
              DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
          .build();

  @Override
  public Map<String, Object> decode(String argumentsJson) {
    try {
      Object decoded = JSON.readValue(argumentsJson, Object.class);
      if (!(decoded instanceof Map<?, ?>)) {
        throw new ProviderToolOperationException(
            ProviderToolOperationException.Code.VALIDATION,
            "SEA provider tool arguments must be a JSON object");
      }
      return org.zalava.api.JsonArguments.immutable(
          JSON.convertValue(decoded, new TypeReference<Map<String, Object>>() {}));
    } catch (ProviderToolOperationException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.VALIDATION,
          "SEA provider tool arguments must be valid JSON",
          ex);
    }
  }
}
