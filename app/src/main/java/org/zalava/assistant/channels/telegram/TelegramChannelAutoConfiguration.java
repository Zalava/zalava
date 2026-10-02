package org.zalava.assistant.channels.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.zalava.assistant.agent.Agent;
import org.zalava.assistant.channels.ChannelRegistry;
import org.zalava.assistant.channels.approval.ChannelApprovalCommands;
import org.zalava.assistant.voice.VoiceProperties;
import org.zalava.assistant.voice.application.port.out.VoiceTranscriptionPort;

@AutoConfiguration
public class TelegramChannelAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "agent.channels.telegram",
      name = {"token", "username"})
  public TelegramChannel telegramChannel(
      @Value("${agent.channels.telegram.token:null}") String botToken,
      @Value("${agent.channels.telegram.username:null}") String allowedUsername,
      Agent agent,
      ChannelRegistry channelRegistry,
      VoiceTranscriptionPort voiceTranscription,
      VoiceProperties voiceProperties,
      ChannelApprovalCommands approvalCommands) {
    return new TelegramChannel(
        botToken,
        allowedUsername,
        agent,
        channelRegistry,
        voiceTranscription,
        voiceProperties,
        approvalCommands);
  }
}
