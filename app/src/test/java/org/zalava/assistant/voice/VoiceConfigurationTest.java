package org.zalava.assistant.voice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalava.assistant.voice.adapter.out.DisabledVoiceTranscription;
import org.zalava.assistant.voice.adapter.out.springai.SpringAiVoiceTranscription;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionPort;

class VoiceConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(VoiceConfiguration.class);

  @Test
  void usesDisabledTranscriptionWhenSpringAiModelIsMissing() {
    contextRunner.run(
        context ->
            assertThat(context)
                .hasSingleBean(VoiceTranscriptionPort.class)
                .getBean(VoiceTranscriptionPort.class)
                .isInstanceOf(DisabledVoiceTranscription.class));
  }

  @Test
  void usesSpringAiTranscriptionWhenModelIsAvailable() {
    contextRunner
        .withUserConfiguration(TranscriptionModelConfiguration.class)
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(VoiceTranscriptionPort.class)
                    .getBean(VoiceTranscriptionPort.class)
                    .isInstanceOf(SpringAiVoiceTranscription.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class TranscriptionModelConfiguration {

    @Bean
    TranscriptionModel transcriptionModel() {
      return new StubTranscriptionModel();
    }
  }

  private static final class StubTranscriptionModel implements TranscriptionModel {

    @Override
    public AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt) {
      return new AudioTranscriptionResponse(new AudioTranscription("transcribed"));
    }
  }
}
