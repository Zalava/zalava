package org.zalava.modules.catalog;

public class SourceModuleIndexValidationException extends IllegalArgumentException {

  public SourceModuleIndexValidationException(String message) {
    super(message);
  }

  public SourceModuleIndexValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
