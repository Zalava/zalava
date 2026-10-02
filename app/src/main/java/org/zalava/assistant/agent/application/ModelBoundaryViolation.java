package org.zalava.assistant.agent.application;

public final class ModelBoundaryViolation extends RuntimeException {
  public ModelBoundaryViolation(String message) {
    super(message);
  }
}
