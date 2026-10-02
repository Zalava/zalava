package org.zalava.assistant.voice.application.port.out;

public interface VoiceTranscriptionPort {

  VoiceTranscriptionResult transcribe(VoiceClip clip);
}
