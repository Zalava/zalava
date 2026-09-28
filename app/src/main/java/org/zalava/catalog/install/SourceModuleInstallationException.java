package org.zalava.catalog.install;

public class SourceModuleInstallationException extends RuntimeException {

  public SourceModuleInstallationException(String message) {
    super(message);
  }

  public SourceModuleInstallationException(String message, Throwable cause) {
    super(message, cause);
  }
}
