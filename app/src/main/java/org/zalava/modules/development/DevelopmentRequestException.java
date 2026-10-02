package org.zalava.modules.development;

public final class DevelopmentRequestException extends RuntimeException {
  public DevelopmentRequestException(String message) {
    super(message);
  }

  public DevelopmentRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
