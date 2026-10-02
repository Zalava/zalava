package org.zalava.assistant.channels.application.port.out;

public interface ChannelProviderOperations {
  void allowUnscoped(String requestId);

  void allowUnscopedTool(String requestId);

  void denyUnscoped(String requestId);
}
