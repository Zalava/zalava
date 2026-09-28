package org.zalava.agent.application.port.out;

/** Raised when a structured model response cannot satisfy the requested result schema. */
public final class StructuredOutputSchemaException extends RuntimeException {
  public StructuredOutputSchemaException(String message, Throwable cause) {
    super(message, cause);
  }
}
