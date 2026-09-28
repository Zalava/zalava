package org.zalava.voice.adapter.out;

import org.zalava.voice.application.port.out.VoiceClip;
import org.zalava.voice.application.port.out.VoiceTranscriptionException;
import org.zalava.voice.application.port.out.VoiceTranscriptionPort;
import org.zalava.voice.application.port.out.VoiceTranscriptionResult;

public final class DisabledVoiceTranscription implements VoiceTranscriptionPort {

  @Override
  public VoiceTranscriptionResult transcribe(VoiceClip clip) {
    throw new VoiceTranscriptionException("Voice transcription is not configured.");
  }
}
