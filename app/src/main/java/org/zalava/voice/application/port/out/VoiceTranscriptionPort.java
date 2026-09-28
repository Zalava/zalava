package org.zalava.voice.application.port.out;

public interface VoiceTranscriptionPort {

  VoiceTranscriptionResult transcribe(VoiceClip clip);
}
