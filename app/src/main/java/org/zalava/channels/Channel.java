package org.zalava.channels;

public interface Channel {

  default String getName() {
    return getClass().getSimpleName();
  }

  void sendMessage(String message);
}
