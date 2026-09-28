package org.zalava.channels.application.port.in;

public record TelegramConfigurationUpdate(
    boolean enabled, String tokenReplacement, String allowedUsername) {}
