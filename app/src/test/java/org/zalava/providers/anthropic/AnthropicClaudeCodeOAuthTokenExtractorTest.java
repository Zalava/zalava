package org.zalava.providers.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnthropicClaudeCodeOAuthTokenExtractorTest {

  @Test
  void extractsAccessTokenFromCredentialsJson() {
    assertThat(
            AnthropicClaudeCodeOAuthTokenExtractor.tokenFromCredentialsJson(
                """
                {
                  "claudeAiOauth": {
                    "accessToken": "token-value",
                    "refreshToken": "refresh-value",
                    "expiresAt": 123
                  }
                }
                """))
        .contains("token-value");
  }

  @Test
  void rejectsBlankOrMalformedCredentialJson() {
    assertThat(
            AnthropicClaudeCodeOAuthTokenExtractor.tokenFromCredentialsJson(
                "{\"claudeAiOauth\": {\"accessToken\": \"  \"}}"))
        .isEmpty();
    assertThat(AnthropicClaudeCodeOAuthTokenExtractor.tokenFromCredentialsJson("not-json"))
        .isEmpty();
  }
}
