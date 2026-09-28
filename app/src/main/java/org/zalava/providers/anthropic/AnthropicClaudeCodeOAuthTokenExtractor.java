package org.zalava.providers.anthropic;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

public class AnthropicClaudeCodeOAuthTokenExtractor {

  private static final Logger log =
      LoggerFactory.getLogger(AnthropicClaudeCodeOAuthTokenExtractor.class);
  private static final String KEYCHAIN_SERVICE = "Claude Code-credentials";
  private static final String LINUX_CREDENTIALS_PATH =
      System.getProperty("user.home") + "/.claude/.credentials.json";

  private static final JsonMapper jsonMapper;
  private static final AtomicReference<CachedToken> cachedToken;

  static {
    jsonMapper = JsonMapper.builder().build();
    cachedToken = new AtomicReference<>();
  }

  public static Optional<String> getToken() {
    CachedToken cached = cachedToken.get();
    if (cached != null && cached.isValid()) {
      return Optional.of(cached.accessToken());
    }

    return readCredentials()
        .filter(c -> c.claudeAiOauth() != null)
        .map(ClaudeCredentials::claudeAiOauth)
        .filter(oauth -> oauth.accessToken() != null && !oauth.accessToken().isBlank())
        .map(
            oauth -> {
              cachedToken.set(new CachedToken(oauth.accessToken(), oauth.expiresAt()));
              return oauth.accessToken();
            });
  }

  private static Optional<ClaudeCredentials> readCredentials() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String json = os.contains("mac") ? readFromMacKeychain() : readFromLinuxFile();

    return parseCredentials(json);
  }

  static Optional<String> tokenFromCredentialsJson(String json) {
    return parseCredentials(json)
        .filter(credentials -> credentials.claudeAiOauth() != null)
        .map(ClaudeCredentials::claudeAiOauth)
        .filter(oauth -> oauth.accessToken() != null && !oauth.accessToken().isBlank())
        .map(OAuthToken::accessToken);
  }

  private static Optional<ClaudeCredentials> parseCredentials(String json) {
    if (json == null || json.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(jsonMapper.readValue(json, ClaudeCredentials.class));
    } catch (Exception e) {
      log.warn("Failed to parse Claude Code credentials: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private static String readFromMacKeychain() {
    try {
      var process =
          new ProcessBuilder("security", "find-generic-password", "-s", KEYCHAIN_SERVICE, "-w")
              .redirectErrorStream(true)
              .start();
      String output;
      try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        var sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        output = sb.toString();
      }
      int exitCode = process.waitFor();
      if (exitCode != 0 || output.isBlank()) {
        log.debug("No Claude Code credentials in keychain (exit code: {})", exitCode);
        return null;
      }
      return output;
    } catch (Exception e) {
      log.debug("Failed to read from macOS keychain: {}", e.getMessage());
      return null;
    }
  }

  private static String readFromLinuxFile() {
    try {
      var path = Path.of(LINUX_CREDENTIALS_PATH);
      if (Files.exists(path)) {
        return Files.readString(path);
      }
      log.debug("No Claude Code credentials file at {}", LINUX_CREDENTIALS_PATH);
      return null;
    } catch (Exception e) {
      log.debug("Failed to read credentials file: {}", e.getMessage());
      return null;
    }
  }

  record ClaudeCredentials(OAuthToken claudeAiOauth) {}

  record OAuthToken(String accessToken, String refreshToken, long expiresAt) {}

  private record CachedToken(String accessToken, long expiresAt) {
    boolean isValid() {
      return expiresAt <= 0 || Instant.now().toEpochMilli() < expiresAt;
    }
  }
}
