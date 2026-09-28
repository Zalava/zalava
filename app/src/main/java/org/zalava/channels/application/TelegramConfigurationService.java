package org.zalava.channels.application;

import java.io.IOException;
import java.util.Map;
import org.zalava.channels.application.port.in.TelegramConfiguration;
import org.zalava.channels.application.port.in.TelegramConfigurationStatus;
import org.zalava.channels.application.port.in.TelegramConfigurationUpdate;
import org.zalava.configuration.application.port.in.ConfigurationCommands;
import org.zalava.configuration.application.port.in.ConfigurationQueries;

/** Applies Telegram-specific validation while delegating persistence to configuration ports. */
public final class TelegramConfigurationService implements TelegramConfiguration {

  private static final String TOKEN = "agent.channels.telegram.token";
  private static final String USERNAME = "agent.channels.telegram.username";

  private final ConfigurationCommands commands;
  private final ConfigurationQueries queries;

  public TelegramConfigurationService(
      ConfigurationCommands commands, ConfigurationQueries queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @Override
  public TelegramConfigurationStatus status() throws IOException {
    Map<String, Object> configuration = queries.readApplicationYaml();
    String token = value(configuration, TOKEN);
    String username = normalizeUsername(value(configuration, USERNAME));
    return new TelegramConfigurationStatus(
        configured(token) && username != null, configured(token), username);
  }

  @Override
  public void update(TelegramConfigurationUpdate update) throws IOException {
    if (!update.enabled()) {
      commands.updateProperties(Map.of(TOKEN, false, USERNAME, false));
      return;
    }

    TelegramConfigurationStatus existing = status();
    String replacement = update.tokenReplacement() == null ? "" : update.tokenReplacement().strip();
    String token = replacement.isEmpty() && existing.tokenConfigured() ? null : replacement;
    String username = normalizeUsername(update.allowedUsername());
    if (token != null && !configured(token)) {
      throw new IllegalArgumentException("Enter the Telegram bot token to enable the channel.");
    }
    if (username == null) {
      throw new IllegalArgumentException(
          "Enter the Telegram username that should be allowed to use the bot.");
    }

    if (token == null) {
      commands.updateProperties(Map.of(USERNAME, username));
    } else {
      commands.updateProperties(Map.of(TOKEN, token, USERNAME, username));
    }
  }

  @SuppressWarnings("unchecked")
  private static String value(Map<String, Object> configuration, String dottedKey) {
    Object value = configuration;
    for (String key : dottedKey.split("\\.")) {
      if (!(value instanceof Map<?, ?> values)) return null;
      value = ((Map<String, Object>) values).get(key);
    }
    return value instanceof String string ? string : null;
  }

  private static boolean configured(String value) {
    return value != null && !value.isBlank() && !"false".equalsIgnoreCase(value);
  }

  private static String normalizeUsername(String username) {
    if (username == null) return null;
    String normalized = username.strip();
    if (normalized.startsWith("@")) normalized = normalized.substring(1);
    return normalized.isBlank() || "false".equalsIgnoreCase(normalized) ? null : normalized;
  }
}
