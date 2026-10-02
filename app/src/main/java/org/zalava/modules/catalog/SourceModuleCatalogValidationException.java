package org.zalava.modules.catalog;

public class SourceModuleCatalogValidationException extends IllegalArgumentException {

  public SourceModuleCatalogValidationException(String message) {
    super(message);
  }

  public SourceModuleCatalogValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
