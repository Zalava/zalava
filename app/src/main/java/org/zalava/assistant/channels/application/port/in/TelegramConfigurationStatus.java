package org.zalava.assistant.channels.application.port.in;

/** Redacted Telegram channel state safe to render in an operator-facing UI. */
public record TelegramConfigurationStatus(
    boolean enabled, boolean tokenConfigured, String allowedUsername) {}
