package org.zalava.voice.adapter.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.zalava.voice.application.port.out.VoiceClip;
import org.zalava.voice.application.port.out.VoiceTranscriptionException;
import org.junit.jupiter.api.Test;

class DisabledVoiceTranscriptionTest {

  @Test
  void failsClosedWhenVoiceTranscriptionIsNotConfigured() {
    DisabledVoiceTranscription transcription = new DisabledVoiceTranscription();

    assertThatThrownBy(
            () -> transcription.transcribe(new VoiceClip(new byte[] {1}, "audio/ogg", "voice.oga")))
        .isInstanceOf(VoiceTranscriptionException.class)
        .hasMessage("Voice transcription is not configured.");
  }
}
