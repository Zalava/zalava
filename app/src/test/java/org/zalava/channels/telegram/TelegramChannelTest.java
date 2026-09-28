package org.zalava.channels.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.zalava.agent.Agent;
import org.zalava.channels.ChannelRegistry;
import org.zalava.channels.approval.ChannelApprovalCommands;
import org.zalava.voice.VoiceProperties;
import org.zalava.voice.application.port.out.VoiceClip;
import org.zalava.voice.application.port.out.VoiceTranscriptionException;
import org.zalava.voice.application.port.out.VoiceTranscriptionPort;
import org.zalava.voice.application.port.out.VoiceTranscriptionResult;

@ExtendWith(MockitoExtension.class)
class TelegramChannelTest {

  @Mock private TelegramClient telegramClient;

  @Mock private Agent agent;

  // -----------------------------------------------------------------------
  // Ignored updates
  // -----------------------------------------------------------------------

  @Test
  void ignoresUpdatesWithoutMessage() {
    TelegramChannel channel = channel("allowed_user");
    Update update = mock(Update.class);
    when(update.hasMessage()).thenReturn(false);

    channel.consume(update);

    verifyNoInteractions(agent, telegramClient);
  }

  @Test
  void ignoresUpdatesWithoutText() {
    TelegramChannel channel = channel("allowed_user");
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasText()).thenReturn(false);
    when(message.hasVoice()).thenReturn(false);

    channel.consume(update);

    verifyNoInteractions(agent, telegramClient);
  }

  @Test
  void ignoresMessagesFromNullUsername() {
    TelegramChannel channel = channel("allowed_user");
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasText()).thenReturn(true);
    when(message.getFrom()).thenReturn(null);

    channel.consume(update);

    verifyNoInteractions(agent, telegramClient);
  }

  @Test
  void ignoresMessagesFromUnauthorizedUser() {
    TelegramChannel channel = channel("allowed_user");

    channel.consume(updateFromUnknownUser("other_user"));

    verify(agent, never()).respondTo(anyString(), anyString());
    verifyNoInteractions(telegramClient);
  }

  @Test
  void ignoresVoiceMessagesFromUnauthorizedUser() {
    TelegramChannel channel = channel("allowed_user");

    channel.consume(voiceUpdateFromUnknownUser("other_user"));

    verify(agent, never()).respondTo(anyString(), anyString());
    verifyNoInteractions(telegramClient);
  }

  // -----------------------------------------------------------------------
  // Username matching
  // -----------------------------------------------------------------------

  @Test
  void usernameMatchingIsCaseInsensitive() throws TelegramApiException {
    TelegramChannel channel = channel("Allowed_User");
    when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

    channel.consume(updateFrom("allowed_user", "hello", 42L, null));

    verify(agent).respondTo(anyString(), anyString());
  }

  @Test
  void stripsLeadingAtFromConfiguredUsername() throws TelegramApiException {
    TelegramChannel channel = channel("@Allowed_User");
    when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

    channel.consume(updateFrom("allowed_user", "hello", 42L, null));

    verify(agent).respondTo(anyString(), anyString());
  }

  @Test
  void stripsLeadingAtFromIncomingUsername() throws TelegramApiException {
    TelegramChannel channel = channel("allowed_user");
    when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

    channel.consume(updateFrom("@allowed_user", "hello", 42L, null));

    verify(agent).respondTo(anyString(), anyString());
  }

  // -----------------------------------------------------------------------
  // Conversation ID and SendMessage
  // -----------------------------------------------------------------------

  @Test
  void usesChannelChatIdAsConversationId() throws TelegramApiException {
    TelegramChannel channel = channel("allowed_user");
    when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

    channel.consume(updateFrom("allowed_user", "hello", 42L, null));

    verify(agent).respondTo(eq("telegram-42"), eq("hello"));
  }

  @Test
  void includesMessageThreadIdInConversationId() throws TelegramApiException {
    TelegramChannel channel = channel("allowed_user");
    when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

    channel.consume(updateFrom("allowed_user", "hello", 42L, 567));

    verify(agent).respondTo(eq("telegram-42-567"), eq("hello"));
  }

  @Test
  void sendsAgentResponseToCorrectChatId() throws TelegramApiException {
    TelegramChannel channel = channel("allowed_user");
    when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

    channel.consume(updateFrom("allowed_user", "hello", 42L, null));

    verify(telegramClient)
        .execute(
            argThat(
                (SendMessage msg) -> "42".equals(msg.getChatId()) && "hi".equals(msg.getText())));
  }

  @Test
  void passesMessageThreadIdToSendMessage() throws TelegramApiException {
    TelegramChannel channel = channel("allowed_user");
    when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

    channel.consume(updateFrom("allowed_user", "hello", 42L, 567));

    verify(telegramClient)
        .execute(
            argThat((SendMessage msg) -> Integer.valueOf(567).equals(msg.getMessageThreadId())));
  }

  @Test
  void doesNotSetMessageThreadIdWhenAbsent() throws TelegramApiException {
    TelegramChannel channel = channel("allowed_user");
    when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

    channel.consume(updateFrom("allowed_user", "hello", 42L, null));

    verify(telegramClient).execute(argThat((SendMessage msg) -> msg.getMessageThreadId() == null));
  }

  @Test
  void handlesApprovalCommandWithoutCallingAgent() throws TelegramApiException {
    ChannelApprovalCommands approvalCommands = mock(ChannelApprovalCommands.class);
    TelegramChannel channel = channelWithApprovalCommands("allowed_user", approvalCommands);
    when(approvalCommands.handle("/sea approve approval-123"))
        .thenReturn(Optional.of("Approval granted once for filesystem-workspace/writeFile."));

    channel.consume(updateFrom("allowed_user", "/sea approve approval-123", 42L, 567));

    verify(agent, never()).respondTo(anyString(), anyString());
    verify(telegramClient)
        .execute(
            argThat(
                (SendMessage msg) ->
                    "42".equals(msg.getChatId())
                        && Integer.valueOf(567).equals(msg.getMessageThreadId())
                        && "Approval granted once for filesystem-workspace/writeFile."
                            .equals(msg.getText())));
  }

  // -----------------------------------------------------------------------
  // sendMessage fallback
  // -----------------------------------------------------------------------

  @Test
  void sendMessageDoesNothingWhenNoChatIdKnown() {
    TelegramChannel channel = channel("allowed_user");

    // No message has been consumed yet, so chatId is unknown
    channel.sendMessage("hello");

    verifyNoInteractions(telegramClient);
  }

  // -----------------------------------------------------------------------
  // Voice messages
  // -----------------------------------------------------------------------

  @Test
  void transcribesAuthorizedVoiceMessageAndSendsTranscriptToAgent() throws Exception {
    CapturingTranscription transcription = new CapturingTranscription("create a task");
    TelegramChannel channel =
        channelWithVoice("allowed_user", transcription, voiceProperties(true, 1024));
    org.telegram.telegrambots.meta.api.objects.File file =
        new org.telegram.telegrambots.meta.api.objects.File(
            "voice-file-id", "voice-unique-id", 4L, "voice.oga");
    lenient().when(telegramClient.execute(isA(GetFile.class))).thenReturn(file);
    when(telegramClient.downloadFileAsStream(file))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
    when(agent.respondTo(anyString(), anyString())).thenReturn("done");

    channel.consume(voiceUpdateFrom("allowed_user", 42L, 567, 4L));

    verify(agent).respondTo(eq("telegram-42-567"), eq("create a task"));
    assertThat(sentMessages())
        .extracting(SendMessage::getText)
        .contains("Transcribing voice message...", "done");
    assertThat(transcription.clips).hasSize(1);
    assertThat(transcription.clips.getFirst().content()).containsExactly(1, 2, 3, 4);
    assertThat(transcription.clips.getFirst().mimeType()).isEqualTo("audio/ogg");
  }

  @Test
  void rejectsVoiceMessageWhenTelegramVoiceIsDisabled() throws Exception {
    CapturingTranscription transcription = new CapturingTranscription("ignored");
    TelegramChannel channel =
        channelWithVoice("allowed_user", transcription, voiceProperties(false, 1024));

    channel.consume(voiceUpdateFrom("allowed_user", 42L, null, 4L));

    verify(agent, never()).respondTo(anyString(), anyString());
    assertThat(sentMessages())
        .extracting(SendMessage::getText)
        .contains("Voice commands are not enabled for Telegram.");
    assertThat(transcription.clips).isEmpty();
  }

  @Test
  void rejectsOversizedVoiceMessageBeforeDownload() throws Exception {
    CapturingTranscription transcription = new CapturingTranscription("ignored");
    TelegramChannel channel =
        channelWithVoice("allowed_user", transcription, voiceProperties(true, 3));

    channel.consume(voiceUpdateFrom("allowed_user", 42L, null, 4L));

    verify(agent, never()).respondTo(anyString(), anyString());
    assertThat(sentMessages())
        .extracting(SendMessage::getText)
        .contains("That voice message is too large to transcribe.");
    assertThat(transcription.clips).isEmpty();
  }

  @Test
  void transcriptionFailureDoesNotCallAgent() throws Exception {
    VoiceTranscriptionPort failingTranscription =
        clip -> {
          throw new VoiceTranscriptionException("Voice transcription failed.");
        };
    TelegramChannel channel =
        channelWithVoice("allowed_user", failingTranscription, voiceProperties(true, 1024));
    org.telegram.telegrambots.meta.api.objects.File file =
        new org.telegram.telegrambots.meta.api.objects.File(
            "voice-file-id", "voice-unique-id", 4L, "voice.oga");
    lenient().when(telegramClient.execute(isA(GetFile.class))).thenReturn(file);
    when(telegramClient.downloadFileAsStream(file))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));

    channel.consume(voiceUpdateFrom("allowed_user", 42L, null, 4L));

    verify(agent, never()).respondTo(anyString(), anyString());
    assertThat(sentMessages())
        .extracting(SendMessage::getText)
        .contains("Voice transcription failed.");
  }

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private TelegramChannel channel(String allowedUsername) {
    return new TelegramChannel(
        "token", allowedUsername, telegramClient, agent, new ChannelRegistry());
  }

  private TelegramChannel channelWithVoice(
      String allowedUsername,
      VoiceTranscriptionPort transcription,
      VoiceProperties voiceProperties) {
    return new TelegramChannel(
        "token",
        allowedUsername,
        telegramClient,
        agent,
        new ChannelRegistry(),
        transcription,
        voiceProperties);
  }

  private TelegramChannel channelWithApprovalCommands(
      String allowedUsername, ChannelApprovalCommands approvalCommands) {
    return new TelegramChannel(
        "token",
        allowedUsername,
        telegramClient,
        agent,
        new ChannelRegistry(),
        new org.zalava.voice.adapter.out.DisabledVoiceTranscription(),
        new VoiceProperties(),
        approvalCommands);
  }

  private VoiceProperties voiceProperties(boolean telegramEnabled, long maxBytes) {
    VoiceProperties properties = new VoiceProperties();
    properties.getTelegram().setEnabled(telegramEnabled);
    properties.setMaxBytes(maxBytes);
    return properties;
  }

  private List<SendMessage> sentMessages() {
    return mockingDetails(telegramClient).getInvocations().stream()
        .map(invocation -> invocation.getArguments()[0])
        .filter(SendMessage.class::isInstance)
        .map(SendMessage.class::cast)
        .toList();
  }

  private Update updateFromUnknownUser(String username) {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);
    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasText()).thenReturn(true);
    when(message.getFrom()).thenReturn(user);
    when(user.getUserName()).thenReturn(username);
    return update;
  }

  private Update voiceUpdateFromUnknownUser(String username) {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);
    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasText()).thenReturn(false);
    when(message.hasVoice()).thenReturn(true);
    when(message.getFrom()).thenReturn(user);
    when(user.getUserName()).thenReturn(username);
    return update;
  }

  private Update updateFrom(String username, String text, long chatId, Integer messageThreadId) {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);

    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasText()).thenReturn(true);
    when(message.getText()).thenReturn(text);
    when(message.getChatId()).thenReturn(chatId);
    when(message.getMessageThreadId()).thenReturn(messageThreadId);
    when(message.getFrom()).thenReturn(user);
    when(user.getUserName()).thenReturn(username);

    return update;
  }

  private Update voiceUpdateFrom(
      String username, long chatId, Integer messageThreadId, Long fileSize) {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);
    Voice voice = mock(Voice.class);

    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasText()).thenReturn(false);
    when(message.hasVoice()).thenReturn(true);
    when(message.getChatId()).thenReturn(chatId);
    when(message.getMessageThreadId()).thenReturn(messageThreadId);
    when(message.getFrom()).thenReturn(user);
    lenient().when(message.getVoice()).thenReturn(voice);
    when(user.getUserName()).thenReturn(username);
    lenient().when(voice.getFileId()).thenReturn("voice-file-id");
    lenient().when(voice.getFileUniqueId()).thenReturn("voice-unique-id");
    lenient().when(voice.getMimeType()).thenReturn("audio/ogg");
    lenient().when(voice.getFileSize()).thenReturn(fileSize);

    return update;
  }

  private static final class CapturingTranscription implements VoiceTranscriptionPort {

    private final String transcript;
    private final List<VoiceClip> clips = new ArrayList<>();

    private CapturingTranscription(String transcript) {
      this.transcript = transcript;
    }

    @Override
    public VoiceTranscriptionResult transcribe(VoiceClip clip) {
      clips.add(clip);
      return new VoiceTranscriptionResult(transcript);
    }
  }
}
