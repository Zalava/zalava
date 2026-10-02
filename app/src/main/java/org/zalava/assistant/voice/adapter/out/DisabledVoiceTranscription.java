package org.zalava.assistant.voice.adapter.out;

import org.zalava.assistant.voice.application.port.out.VoiceClip;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionException;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionPort;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionResult;

public final class DisabledVoiceTranscription implements VoiceTranscriptionPort {

  @Override
  public VoiceTranscriptionResult transcribe(VoiceClip clip) {
    throw new VoiceTranscriptionException("Voice transcription is not configured.");
  }
}
