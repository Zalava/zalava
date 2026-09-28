package org.zalava.chat.application.port.out;

public interface ChatMessageEvents {
  void publishReceived(String channelName, String message);
}
