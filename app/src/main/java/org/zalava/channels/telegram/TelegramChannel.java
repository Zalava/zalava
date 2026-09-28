package org.zalava.channels.telegram;

import static java.util.Optional.ofNullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.zalava.agent.Agent;
import org.zalava.channels.Channel;
import org.zalava.channels.ChannelMessageReceivedEvent;
import org.zalava.channels.ChannelRegistry;
import org.zalava.channels.approval.ChannelApprovalCommands;
import org.zalava.voice.VoiceProperties;
import org.zalava.voice.adapter.out.DisabledVoiceTranscription;
import org.zalava.voice.application.port.out.VoiceClip;
import org.zalava.voice.application.port.out.VoiceTranscriptionException;
import org.zalava.voice.application.port.out.VoiceTranscriptionPort;

public class TelegramChannel
    implements Channel, SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

  private static final Logger log = LoggerFactory.getLogger(TelegramChannel.class);
  private final String botToken;
  private final String allowedUsername;
  private final TelegramClient telegramClient;
  private final Agent agent;
  private final ChannelRegistry channelRegistry;
  private final VoiceTranscriptionPort voiceTranscription;
  private final VoiceProperties voiceProperties;
  private final ChannelApprovalCommands approvalCommands;
  private Long chatId;
  private Integer messageThreadId;

  public TelegramChannel(
      String botToken, String allowedUsername, Agent agent, ChannelRegistry channelRegistry) {
    this(
        botToken,
        allowedUsername,
        new OkHttpTelegramClient(botToken),
        agent,
        channelRegistry,
        new DisabledVoiceTranscription(),
        new VoiceProperties());
  }

  public TelegramChannel(
      String botToken,
      String allowedUsername,
      Agent agent,
      ChannelRegistry channelRegistry,
      VoiceTranscriptionPort voiceTranscription,
      VoiceProperties voiceProperties) {
    this(
        botToken,
        allowedUsername,
        new OkHttpTelegramClient(botToken),
        agent,
        channelRegistry,
        voiceTranscription,
        voiceProperties,
        null);
  }

  public TelegramChannel(
      String botToken,
      String allowedUsername,
      Agent agent,
      ChannelRegistry channelRegistry,
      VoiceTranscriptionPort voiceTranscription,
      VoiceProperties voiceProperties,
      ChannelApprovalCommands approvalCommands) {
    this(
        botToken,
        allowedUsername,
        new OkHttpTelegramClient(botToken),
        agent,
        channelRegistry,
        voiceTranscription,
        voiceProperties,
        approvalCommands);
  }

  TelegramChannel(
      String botToken,
      String allowedUsername,
      TelegramClient telegramClient,
      Agent agent,
      ChannelRegistry channelRegistry) {
    this(
        botToken,
        allowedUsername,
        telegramClient,
        agent,
        channelRegistry,
        new DisabledVoiceTranscription(),
        new VoiceProperties());
  }

  TelegramChannel(
      String botToken,
      String allowedUsername,
      TelegramClient telegramClient,
      Agent agent,
      ChannelRegistry channelRegistry,
      VoiceTranscriptionPort voiceTranscription,
      VoiceProperties voiceProperties) {
    this(
        botToken,
        allowedUsername,
        telegramClient,
        agent,
        channelRegistry,
        voiceTranscription,
        voiceProperties,
        null);
  }

  TelegramChannel(
      String botToken,
      String allowedUsername,
      TelegramClient telegramClient,
      Agent agent,
      ChannelRegistry channelRegistry,
      VoiceTranscriptionPort voiceTranscription,
      VoiceProperties voiceProperties,
      ChannelApprovalCommands approvalCommands) {
    this.botToken = botToken;
    this.allowedUsername = normalizeUsername(allowedUsername);
    this.telegramClient = telegramClient;
    this.agent = agent;
    this.channelRegistry = channelRegistry;
    this.voiceTranscription = voiceTranscription;
    this.voiceProperties = voiceProperties;
    this.approvalCommands = approvalCommands;
    channelRegistry.registerChannel(this);
    log.info("Started Telegram integration");
  }

  @Override
  public String getBotToken() {
    return botToken;
  }

  @Override
  public LongPollingUpdateConsumer getUpdatesConsumer() {
    return this;
  }

  @Override
  public void consume(Update update) {
    if (!update.hasMessage()) return;

    Message requestMessage = update.getMessage();
    if (!(requestMessage.hasText() || requestMessage.hasVoice())) return;

    String userName =
        requestMessage.getFrom() == null ? null : requestMessage.getFrom().getUserName();
    if (!isAllowedUser(userName)) {
      log.warn("Ignoring Telegram message from unauthorized username '{}'", userName);
      sendMessage("I'm sorry, I don't accept instructions from you.");
      return;
    }

    this.chatId = requestMessage.getChatId();
    this.messageThreadId = requestMessage.getMessageThreadId();
    if (requestMessage.hasText()) {
      handleTextMessage(requestMessage);
    } else {
      handleVoiceMessage(requestMessage);
    }
  }

  @Override
  public void sendMessage(String message) {
    if (chatId == null) {
      log.error("No known chatId, cannot send message '{}'", message);
      return;
    }
    sendMessage(chatId, null, message);
  }

  public void sendMessage(long chatId, Integer messageThreadId, String message) {
    SendMessage messageMessage =
        SendMessage.builder().chatId(chatId).messageThreadId(messageThreadId).text(message).build();
    try {
      telegramClient.execute(messageMessage);
    } catch (TelegramApiException e) {
      throw new RuntimeException(e);
    }
  }

  private void handleTextMessage(Message requestMessage) {
    String messageText = requestMessage.getText();
    if (approvalCommands != null) {
      var response = approvalCommands.handle(messageText);
      if (response.isPresent()) {
        sendMessage(chatId, messageThreadId, response.get());
        return;
      }
    }
    respondToUserMessage(messageText);
  }

  private void handleVoiceMessage(Message requestMessage) {
    if (!voiceProperties.getTelegram().isEnabled()) {
      sendMessage(chatId, messageThreadId, "Voice commands are not enabled for Telegram.");
      return;
    }

    org.telegram.telegrambots.meta.api.objects.Voice voice = requestMessage.getVoice();
    if (voice == null || voice.getFileId() == null || voice.getFileId().isBlank()) {
      sendMessage(chatId, messageThreadId, "I could not read that Telegram voice message.");
      return;
    }
    if (isOversized(voice.getFileSize())) {
      sendMessage(chatId, messageThreadId, "That voice message is too large to transcribe.");
      return;
    }

    try {
      if (voiceProperties.getStatusMessages().isEnabled()) {
        sendMessage(chatId, messageThreadId, "Transcribing voice message...");
      }
      org.telegram.telegrambots.meta.api.objects.File file =
          telegramClient.execute(GetFile.builder().fileId(voice.getFileId()).build());
      if (isOversized(file.getFileSize())) {
        sendMessage(chatId, messageThreadId, "That voice message is too large to transcribe.");
        return;
      }
      try (InputStream input = telegramClient.downloadFileAsStream(file)) {
        byte[] audio = readBounded(input, voiceProperties.getMaxBytes());
        String source =
            "telegram-voice-"
                + ofNullable(voice.getFileUniqueId()).orElse(voice.getFileId())
                + ".oga";
        String transcript =
            voiceTranscription.transcribe(new VoiceClip(audio, voice.getMimeType(), source)).text();
        respondToUserMessage(transcript);
      }
    } catch (VoiceTranscriptionException ex) {
      log.warn("Telegram voice transcription failed: {}", ex.getMessage());
      sendMessage(chatId, messageThreadId, ex.getMessage());
    } catch (TelegramApiException | IOException ex) {
      log.warn("Telegram voice message processing failed", ex);
      sendMessage(chatId, messageThreadId, "I could not process that Telegram voice message.");
    }
  }

  private void respondToUserMessage(String messageText) {
    channelRegistry.publishMessageReceivedEvent(
        new TelegramChannelMessageReceivedEvent(getName(), messageText, chatId, messageThreadId));
    String response = agent.respondTo(getConversationId(chatId, messageThreadId), messageText);
    sendMessage(chatId, messageThreadId, response);
  }

  private boolean isOversized(Long fileSize) {
    return fileSize != null && fileSize > voiceProperties.getMaxBytes();
  }

  private static byte[] readBounded(InputStream input, long maxBytes) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    long total = 0;
    int count;
    while ((count = input.read(buffer)) != -1) {
      total += count;
      if (total > maxBytes) {
        throw new VoiceTranscriptionException("That voice message is too large to transcribe.");
      }
      output.write(buffer, 0, count);
    }
    return output.toByteArray();
  }

  private boolean isAllowedUser(String userName) {
    String normalizedUserName = normalizeUsername(userName);
    return normalizedUserName != null && normalizedUserName.equalsIgnoreCase(allowedUsername);
  }

  private static String normalizeUsername(String userName) {
    if (userName == null) {
      return null;
    }

    String normalizedUserName = userName.trim();
    if (normalizedUserName.startsWith("@")) {
      normalizedUserName = normalizedUserName.substring(1);
    }

    return normalizedUserName.isBlank() ? null : normalizedUserName;
  }

  private String getConversationId(Long chatId, Integer messageThreadId) {
    return "telegram-" + chatId + ofNullable(messageThreadId).map(i -> "-" + i).orElse("");
  }

  static class TelegramChannelMessageReceivedEvent extends ChannelMessageReceivedEvent {

    private final long chatId;
    private final Integer messageThreadId;

    public TelegramChannelMessageReceivedEvent(
        String channel, String message, long chatId, Integer messageThreadId) {
      super(channel, message);
      this.chatId = chatId;
      this.messageThreadId = messageThreadId;
    }

    public long getChatId() {
      return chatId;
    }

    public Integer getMessageThreadId() {
      return messageThreadId;
    }
  }
}
