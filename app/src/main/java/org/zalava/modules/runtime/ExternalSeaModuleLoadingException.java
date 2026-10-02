package org.zalava.modules.runtime;

public final class ExternalSeaModuleLoadingException extends RuntimeException {

  public ExternalSeaModuleLoadingException(String message) {
    super(message);
  }

  public ExternalSeaModuleLoadingException(String message, Throwable cause) {
    super(message, cause);
  }
}
