package org.zalava.api.extensions.channels;

/** Features a channel transport can render or accept. */
public record ChannelCapabilities(
    boolean text,
    boolean streaming,
    boolean markup,
    boolean attachments,
    boolean audio,
    boolean interactiveActions,
    boolean approvalPrompts,
    boolean proactiveNotifications) {
  public static ChannelCapabilities textOnly() {
    return new ChannelCapabilities(true, false, false, false, false, false, false, false);
  }
}
