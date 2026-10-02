package org.zalava.assistant.channels;

public interface Channel {

  default String getName() {
    return getClass().getSimpleName();
  }

  void sendMessage(String message);
}
