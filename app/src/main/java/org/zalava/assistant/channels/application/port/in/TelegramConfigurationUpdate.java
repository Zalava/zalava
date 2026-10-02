package org.zalava.assistant.channels.application.port.in;

public record TelegramConfigurationUpdate(
    boolean enabled, String tokenReplacement, String allowedUsername) {}
