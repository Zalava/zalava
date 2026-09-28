package org.zalava.providers.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.core.http.HttpMethod;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class AnthropicClaudeCodeBackendMoreTest {

  private final AnthropicClaudeCodeBackend backend = new AnthropicClaudeCodeBackend();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void prepareRequestLeavesBodiesWithoutContentUnwrapped() {
    HttpRequest prepared = backend.prepareRequest(buildRequest(null));

    assertThat(prepared.body()).isNull();
  }

  @Test
  void authorizeRequestReplacesApiKeyAuthWithTheInjectedClaudeCodeToken() {
    HttpRequest request = buildRequest(minimalBody());

    HttpRequest authorized =
        new AnthropicClaudeCodeBackend(() -> Optional.of("test-token")).authorizeRequest(request);
    // The injected token replaces the API key with bearer authentication.
    assertThat(authorized.headers().values("Authorization").getFirst())
        .isEqualTo("Bearer test-token");
    assertThat(authorized.headers().values("x-api-key")).isEmpty();
    assertThat(authorized.headers().values("anthropic-beta"))
        .containsExactly("claude-code-20250219,oauth-2025-04-20");
  }

  @Test
  void authorizeRequestFailsClosedWhenTheInjectedTokenIsMissing() {
    AnthropicClaudeCodeBackend tokenlessBackend =
        new AnthropicClaudeCodeBackend(Optional::<String>empty);

    assertThatThrownBy(() -> tokenlessBackend.authorizeRequest(buildRequest(minimalBody())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No valid Claude Code OAuth token found");
  }

  @Test
  void contentLengthAndRepeatableReflectTheModifiedBody() throws Exception {
    HttpRequest prepared = backend.prepareRequest(buildRequest(minimalBody()));

    HttpRequestBody body = prepared.body();
    assertThat(body.repeatable()).isTrue();
    assertThat(body.contentLength()).isEqualTo(bytesOf(body).length);
    assertThat(body.contentType()).isEqualTo("application/json");
  }

  @Test
  void malformedOriginalBodiesAreForwardedUnchanged() throws Exception {
    HttpRequestBody malformed =
        new HttpRequestBody() {
          @Override
          public void writeTo(OutputStream os) {
            try {
              os.write("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public String contentType() {
            return "application/json";
          }

          @Override
          public long contentLength() {
            return "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
          }

          @Override
          public boolean repeatable() {
            return true;
          }

          @Override
          public void close() {}
        };

    HttpRequest prepared =
        backend.prepareRequest(
            HttpRequest.builder()
                .method(HttpMethod.POST)
                .baseUrl("https://api.anthropic.com")
                .addPathSegment("v1")
                .addPathSegment("messages")
                .body(malformed)
                .build());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    prepared.body().writeTo(out);
    assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("not-json");
  }

  @Test
  void closedDelegatesAreClosed() {
    backend.close();
    assertThat(backend).isNotNull();
  }

  @Test
  void baseUrlDelegatesToTheStandardAnthropicEndpoint() {
    assertThat(backend.baseUrl()).isEqualTo("https://api.anthropic.com");
  }

  private ObjectNode minimalBody() {
    ObjectNode requestBody = objectMapper.createObjectNode();
    requestBody.put("model", "claude-sonnet-4-6");
    requestBody.put("max_tokens", 16);
    return requestBody;
  }

  private HttpRequest buildRequest(ObjectNode body) {
    HttpRequest.Builder builder =
        HttpRequest.builder()
            .method(HttpMethod.POST)
            .baseUrl("https://api.anthropic.com")
            .addPathSegment("v1")
            .addPathSegment("messages");
    if (body != null) {
      byte[] bytes;
      try {
        bytes = objectMapper.writeValueAsBytes(body);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      builder.body(wrappingBody(bytes));
    }
    return builder.build();
  }

  private HttpRequestBody wrappingBody(byte[] bytes) {
    return new HttpRequestBody() {
      @Override
      public void writeTo(OutputStream os) {
        try {
          os.write(bytes);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public String contentType() {
        return "application/json";
      }

      @Override
      public long contentLength() {
        return bytes.length;
      }

      @Override
      public boolean repeatable() {
        return true;
      }

      @Override
      public void close() {}
    };
  }

  private byte[] bytesOf(HttpRequestBody body) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    body.writeTo(out);
    return out.toByteArray();
  }

  @Test
  void systemArrayEntriesArePreservedThroughPrefixInjection() throws Exception {
    ObjectNode requestBody = objectMapper.createObjectNode();
    ArrayNode existingSystem = objectMapper.createArrayNode();
    ObjectNode block = objectMapper.createObjectNode();
    block.put("type", "text");
    block.put("text", "Existing block.");
    existingSystem.add(block);
    requestBody.set("system", existingSystem);

    HttpRequest prepared = backend.prepareRequest(buildRequest(requestBody));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    prepared.body().writeTo(out);
    JsonNode result = objectMapper.readTree(out.toByteArray());

    assertThat(result.get("system").size()).isEqualTo(2);
    assertThat(result.get("system").get(1).get("text").asString()).isEqualTo("Existing block.");
  }

  @Test
  void systemPrefixInjectionSurvivesRoundTripsWithoutModelOrSystem() throws Exception {
    ObjectNode requestBody = objectMapper.createObjectNode();
    requestBody.put("max_tokens", 32);

    HttpRequest prepared = backend.prepareRequest(buildRequest(requestBody));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    prepared.body().writeTo(out);
    JsonNode result = objectMapper.readTree(out.toByteArray());

    assertThat(result.get("system").size()).isEqualTo(1);
    assertThat(result.get("system").get(0).get("cache_control").get("type").asString())
        .isEqualTo("ephemeral");
    assertThat(result.get("max_tokens").asInt()).isEqualTo(32);
  }
}
