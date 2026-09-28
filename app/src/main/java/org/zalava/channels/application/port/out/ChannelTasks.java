package org.zalava.channels.application.port.out;

public interface ChannelTasks {
  boolean isAwaitingHumanInput(String taskReference);

  void resume(String taskReference);
}
