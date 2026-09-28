package org.zalava.fixture.speech;

import java.util.Map;
import java.util.Optional;
import org.zalava.SeaServiceFactoryContext;
import org.zalava.speech.SpeechException;

/** Shared scoped configuration and secret lookup for the fixture speech providers. */
final class MockSpeechConfiguration {
  static final String MODULE_ID = "sea-external-module-fixture";
  static final String CREDENTIAL_REFERENCE = "credentialRef";
  static final String MAX_BYTES = "maxBytes";
  static final String TRANSCRIPT = "transcript";
  static final String CHUNK_SIZE_BYTES = "chunkSizeBytes";

  private MockSpeechConfiguration() {}

  /**
   * Resolves the optional scoped secret reference. When a {@code credentialRef} is configured but
   * the secret cannot be resolved, creation fails closed with the typed contract exception.
   */
  static void requireSecret(SeaServiceFactoryContext context) {
    Object reference = context.configuration().get(CREDENTIAL_REFERENCE);
    if (reference == null) {
      return;
    }
    if (!(reference instanceof String value) || value.isBlank()) {
      throw new SpeechException.ConfigurationException(
          "Speech fixture " + CREDENTIAL_REFERENCE + " must be a non-blank secret reference");
    }
    Optional<char[]> secret = context.secrets().resolve(value);
    if (secret.isEmpty() || secret.orElseThrow().length == 0) {
      throw new SpeechException.ConfigurationException(
          "Speech fixture credential is unavailable: " + value);
    }
  }

  static long positiveLong(Map<String, Object> configuration, String key, long fallback) {
    Object value = configuration.get(key);
    if (value == null) {
      return fallback;
    }
    long parsed = number(value, key);
    if (parsed < 1) {
      throw new SpeechException.ConfigurationException(key + " must be positive");
    }
    return parsed;
  }

  static int positiveInt(Map<String, Object> configuration, String key, int fallback) {
    return Math.toIntExact(positiveLong(configuration, key, fallback));
  }

  static String text(Map<String, Object> configuration, String key, String fallback) {
    Object value = configuration.get(key);
    if (value instanceof String text && !text.isBlank()) {
      return text;
    }
    return fallback;
  }

  private static long number(Object value, String key) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new SpeechException.ConfigurationException(key + " must be a number");
  }
}
