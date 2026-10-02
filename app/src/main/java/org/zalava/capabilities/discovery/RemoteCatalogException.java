package org.zalava.capabilities.discovery;

/** Raised when a configured remote module catalog is missing, malformed or unreachable. */
public final class RemoteCatalogException extends RuntimeException {

  public RemoteCatalogException(String message) {
    super(message);
  }

  public RemoteCatalogException(String message, Throwable cause) {
    super(message, cause);
  }
}
