package org.zalava.voice.application.port.out;

public class VoiceTranscriptionException extends RuntimeException {

  public VoiceTranscriptionException(String message) {
    super(message);
  }

  public VoiceTranscriptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
