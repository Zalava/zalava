package org.zalava.capabilities.operation.application.port.in;

public final class ProviderToolOperationException extends RuntimeException {

  private final Code code;

  public ProviderToolOperationException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public ProviderToolOperationException(Code code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public Code code() {
    return code;
  }

  public enum Code {
    PROVIDER_NOT_FOUND,
    TOOL_NOT_FOUND,
    APPROVAL_NOT_FOUND,
    APPROVAL_CONFLICT,
    UNSUPPORTED,
    VALIDATION,
    EXECUTION
  }
}
