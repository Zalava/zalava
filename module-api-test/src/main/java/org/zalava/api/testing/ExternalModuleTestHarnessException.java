package org.zalava.api.testing;

/** Raised when an external module cannot be proven loadable under the Zalava module boundary. */
public final class ExternalModuleTestHarnessException extends RuntimeException {

  public ExternalModuleTestHarnessException(String message) {
    super(message);
  }

  public ExternalModuleTestHarnessException(String message, Throwable cause) {
    super(message, cause);
  }
}
