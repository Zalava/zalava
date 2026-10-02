package org.zalava.assistant.voice.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.zalava.assistant.voice.application.port.out.VoiceClip;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionException;

class SpringAiVoiceTranscriptionTest {

  @Test
  void delegatesVoiceClipToTranscriptionModel() {
    CapturingTranscriptionModel model = new CapturingTranscriptionModel(" transcribed command ");
    SpringAiVoiceTranscription transcription = new SpringAiVoiceTranscription(model);

    var result =
        transcription.transcribe(new VoiceClip(new byte[] {1, 2, 3}, "audio/ogg", "telegram.oga"));

    assertThat(result.text()).isEqualTo("transcribed command");
    assertThat(model.prompt.getInstructions().getFilename()).isEqualTo("telegram.oga");
  }

  @Test
  void wrapsProviderFailure() {
    SpringAiVoiceTranscription transcription =
        new SpringAiVoiceTranscription(
            prompt -> {
              throw new IllegalStateException("provider unavailable");
            });

    assertThatThrownBy(
            () ->
                transcription.transcribe(
                    new VoiceClip(new byte[] {1}, "audio/ogg", "telegram.oga")))
        .isInstanceOf(VoiceTranscriptionException.class)
        .hasMessage("Voice transcription failed.")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  private static final class CapturingTranscriptionModel implements TranscriptionModel {

    private final String transcript;
    private AudioTranscriptionPrompt prompt;

    private CapturingTranscriptionModel(String transcript) {
      this.transcript = transcript;
    }

    @Override
    public AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt) {
      this.prompt = transcriptionPrompt;
      return new AudioTranscriptionResponse(new AudioTranscription(transcript));
    }
  }
}
