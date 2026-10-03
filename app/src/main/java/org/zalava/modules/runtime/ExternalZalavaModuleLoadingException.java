package org.zalava.modules.runtime;

public final class ExternalZalavaModuleLoadingException extends RuntimeException {

  public ExternalZalavaModuleLoadingException(String message) {
    super(message);
  }

  public ExternalZalavaModuleLoadingException(String message, Throwable cause) {
    super(message, cause);
  }
}
