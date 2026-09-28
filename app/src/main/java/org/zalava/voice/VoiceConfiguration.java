package org.zalava.voice;

import org.zalava.voice.adapter.out.DisabledVoiceTranscription;
import org.zalava.voice.adapter.out.springai.SpringAiVoiceTranscription;
import org.zalava.voice.application.port.out.VoiceTranscriptionPort;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VoiceProperties.class)
public class VoiceConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public VoiceTranscriptionPort voiceTranscription(
      ObjectProvider<TranscriptionModel> transcriptionModel) {
    TranscriptionModel model = transcriptionModel.getIfAvailable();
    if (model == null) {
      return new DisabledVoiceTranscription();
    }
    return new SpringAiVoiceTranscription(model);
  }
}
