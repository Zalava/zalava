package org.zalava.assistant.channels.application;

public final class ChannelProviderOperationException extends RuntimeException {
  private final Code code;

  public ChannelProviderOperationException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }

  public enum Code {
    APPROVAL_NOT_FOUND,
    APPROVAL_CONFLICT,
    OTHER
  }
}
