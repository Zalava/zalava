package org.zalava.assistant.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.voice")
public class VoiceProperties {

  private long maxBytes = 10L * 1024L * 1024L;
  private final Telegram telegram = new Telegram();
  private final StatusMessages statusMessages = new StatusMessages();

  public long getMaxBytes() {
    return maxBytes;
  }

  public void setMaxBytes(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  public Telegram getTelegram() {
    return telegram;
  }

  public StatusMessages getStatusMessages() {
    return statusMessages;
  }

  public static final class Telegram {

    private boolean enabled = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static final class StatusMessages {

    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
