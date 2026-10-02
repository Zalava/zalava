package org.zalava.assistant.voice.application.port.out;

public record VoiceTranscriptionResult(String text) {

  public VoiceTranscriptionResult {
    if (text == null || text.isBlank()) {
      throw new VoiceTranscriptionException("Voice transcription returned no text.");
    }
    text = text.trim();
  }
}
