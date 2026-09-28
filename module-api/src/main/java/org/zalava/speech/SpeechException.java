package org.zalava.speech;

/** Typed failure for every speech contract violation and provider fault. */
public class SpeechException extends RuntimeException {

  public SpeechException(String message) {
    super(message);
  }

  public SpeechException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Raised when a session is used after closure. */
  public static final class ClosedSessionException extends SpeechException {

    public ClosedSessionException(String message) {
      super(message);
    }
  }

  /** Raised when the requested audio format is not among the declared capabilities. */
  public static final class UnsupportedFormatException extends SpeechException {

    public UnsupportedFormatException(String message) {
      super(message);
    }
  }

  /** Raised when the declared byte or duration bounds are exceeded. */
  public static final class LimitExceededException extends SpeechException {

    public LimitExceededException(String message) {
      super(message);
    }
  }

  /** Raised when the operation's deadline elapsed before completion. */
  public static final class DeadlineExceededException extends SpeechException {

    public DeadlineExceededException(String message) {
      super(message);
    }
  }

  /** Raised when an operation was cancelled before completion. */
  public static final class CancelledException extends SpeechException {

    public CancelledException(String message) {
      super(message);
    }
  }

  /** Raised when scoped configuration or secrets required by a provider are missing or invalid. */
  public static final class ConfigurationException extends SpeechException {

    public ConfigurationException(String message) {
      super(message);
    }
  }
}
