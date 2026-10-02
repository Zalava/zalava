package org.zalava.assistant.chat.application.port.out;

public interface ChatMessageEvents {
  void publishReceived(String channelName, String message);
}
