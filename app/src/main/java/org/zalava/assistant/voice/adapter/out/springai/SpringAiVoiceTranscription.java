package org.zalava.assistant.voice.adapter.out.springai;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.ByteArrayResource;
import org.zalava.assistant.voice.application.port.out.VoiceClip;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionException;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionPort;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionResult;

public final class SpringAiVoiceTranscription implements VoiceTranscriptionPort {

  private final TranscriptionModel transcriptionModel;

  public SpringAiVoiceTranscription(TranscriptionModel transcriptionModel) {
    this.transcriptionModel = transcriptionModel;
  }

  @Override
  public VoiceTranscriptionResult transcribe(VoiceClip clip) {
    try {
      String transcript = transcriptionModel.transcribe(new VoiceClipResource(clip));
      return new VoiceTranscriptionResult(transcript);
    } catch (VoiceTranscriptionException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new VoiceTranscriptionException("Voice transcription failed.", ex);
    }
  }

  private static final class VoiceClipResource extends ByteArrayResource {

    private final VoiceClip clip;

    private VoiceClipResource(VoiceClip clip) {
      super(clip.content());
      this.clip = clip;
    }

    @Override
    public String getFilename() {
      return clip.source();
    }

    @Override
    public String getDescription() {
      return "Voice clip from " + clip.source();
    }
  }
}
