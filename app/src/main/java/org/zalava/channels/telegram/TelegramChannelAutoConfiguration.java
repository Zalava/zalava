package org.zalava.channels.telegram;

import org.zalava.agent.Agent;
import org.zalava.channels.ChannelRegistry;
import org.zalava.channels.approval.ChannelApprovalCommands;
import org.zalava.voice.VoiceProperties;
import org.zalava.voice.application.port.out.VoiceTranscriptionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

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
