package org.zalava.channels.application.port.in;

import java.io.IOException;

/** Local-owner configuration for the optional Telegram channel. */
public interface TelegramConfiguration {

  TelegramConfigurationStatus status() throws IOException;

  void update(TelegramConfigurationUpdate update) throws IOException;
}
