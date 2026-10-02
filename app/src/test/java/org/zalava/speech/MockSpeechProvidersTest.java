package org.zalava.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.api.extensions.speech.SpeechException;
import org.zalava.api.extensions.speech.SpeechRecognition;
import org.zalava.api.extensions.speech.SpeechSynthesis;

class MockSpeechProvidersTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @TempDir Path workspace;

  @Test
  void declaresAndNegotiatesFormats() throws Exception {
    try (SpeechFixtureModule fixture =
        SpeechFixtureModule.start(workspace, SpeechFixtureModule.configuration())) {
      SpeechRecognition recognition = fixture.recognition();
      SpeechSynthesis synthesis = fixture.synthesis();

      assertThat(recognition.capabilities().streaming()).isTrue();
      assertThat(recognition.capabilities().formats()).containsExactly(SpeechFixtureModule.FORMAT);
      assertThat(synthesis.capabilities().formats()).containsExactly(SpeechFixtureModule.FORMAT);

      assertThatThrownBy(
              () ->
                  recognition.open(
                      new SpeechRecognition.Request(
                          Set.of(SpeechFixtureModule.UNSUPPORTED_FORMAT), 10, TIMEOUT)))
          .isInstanceOf(SpeechException.UnsupportedFormatException.class);
      assertThatThrownBy(
              () ->
                  synthesis.open(
                      new SpeechSynthesis.Request(
                          SpeechFixtureModule.UNSUPPORTED_FORMAT, "hello", 10, TIMEOUT)))
          .isInstanceOf(SpeechException.UnsupportedFormatException.class);
    }
  }

  @Test
  void rejectsRequestsAndInputOutsideTheDeclaredByteBounds() throws Exception {
    try (SpeechFixtureModule fixture =
        SpeechFixtureModule.start(workspace, SpeechFixtureModule.configuration())) {
      SpeechRecognition recognition = fixture.recognition();
      SpeechSynthesis synthesis = fixture.synthesis();

      assertThatThrownBy(
              () ->
                  recognition.open(
                      new SpeechRecognition.Request(
                          Set.of(SpeechFixtureModule.FORMAT), 4097, TIMEOUT)))
          .isInstanceOf(SpeechException.LimitExceededException.class);

      SpeechRecognition.RecognitionSession recognitionSession =
          recognition.open(
              new SpeechRecognition.Request(Set.of(SpeechFixtureModule.FORMAT), 4, TIMEOUT));
      assertThatThrownBy(() -> recognitionSession.acceptChunk(new byte[5]))
          .isInstanceOf(SpeechException.LimitExceededException.class);

      SpeechSynthesis.SynthesisSession synthesisSession =
          synthesis.open(
              new SpeechSynthesis.Request(SpeechFixtureModule.FORMAT, "hello", 1, TIMEOUT));
      assertThatThrownBy(synthesisSession::nextChunk)
          .isInstanceOf(SpeechException.LimitExceededException.class);
    }
  }

  @Test
  void echoesTheScopedTranscriptForRecognition() throws Exception {
    try (SpeechFixtureModule fixture =
        SpeechFixtureModule.start(
            workspace, SpeechFixtureModule.configuration("transcript", "hello from config"))) {
      SpeechRecognition.RecognitionSession session =
          fixture
              .recognition()
              .open(new SpeechRecognition.Request(Set.of(SpeechFixtureModule.FORMAT), 16, TIMEOUT));

      session.acceptChunk(new byte[] {1, 2, 3});

      assertThat(session.endInput().text()).isEqualTo("hello from config");
    }
  }

  @Test
  void enforcesRecognitionAndSynthesisDeadlines() throws Exception {
    try (SpeechFixtureModule fixture =
        SpeechFixtureModule.start(workspace, SpeechFixtureModule.configuration())) {
      Duration expired = Duration.ofMillis(20);
      SpeechRecognition.RecognitionSession recognitionSession =
          fixture
              .recognition()
              .open(new SpeechRecognition.Request(Set.of(SpeechFixtureModule.FORMAT), 16, expired));
      SpeechSynthesis.SynthesisSession synthesisSession =
          fixture
              .synthesis()
              .open(new SpeechSynthesis.Request(SpeechFixtureModule.FORMAT, "hello", 16, expired));

      Thread.sleep(40);

      assertThatThrownBy(recognitionSession::endInput)
          .isInstanceOf(SpeechException.DeadlineExceededException.class);
      assertThatThrownBy(synthesisSession::nextChunk)
          .isInstanceOf(SpeechException.DeadlineExceededException.class);
    }
  }

  @Test
  void cancelsSessionsAndRejectsUseAfterClose() throws Exception {
    try (SpeechFixtureModule fixture =
        SpeechFixtureModule.start(workspace, SpeechFixtureModule.configuration())) {
      SpeechRecognition.RecognitionSession cancelled =
          fixture
              .recognition()
              .open(new SpeechRecognition.Request(Set.of(SpeechFixtureModule.FORMAT), 16, TIMEOUT));
      cancelled.cancel();
      assertThatThrownBy(cancelled::endInput)
          .isInstanceOf(SpeechException.CancelledException.class);
      assertThatThrownBy(() -> cancelled.acceptChunk(new byte[] {1}))
          .isInstanceOf(SpeechException.CancelledException.class);

      SpeechSynthesis.SynthesisSession synthesisSession =
          fixture
              .synthesis()
              .open(new SpeechSynthesis.Request(SpeechFixtureModule.FORMAT, "hello", 16, TIMEOUT));
      synthesisSession.cancel();
      assertThatThrownBy(synthesisSession::nextChunk)
          .isInstanceOf(SpeechException.CancelledException.class);

      SpeechRecognition.RecognitionSession closed =
          fixture
              .recognition()
              .open(new SpeechRecognition.Request(Set.of(SpeechFixtureModule.FORMAT), 16, TIMEOUT));
      closed.close();
      closed.close();
      assertThatThrownBy(closed::endInput)
          .isInstanceOf(SpeechException.ClosedSessionException.class);
    }
  }

  @Test
  void streamsSynthesisInDeterministicBoundedChunks() throws Exception {
    try (SpeechFixtureModule fixture =
        SpeechFixtureModule.start(
            workspace, SpeechFixtureModule.configuration("chunkSizeBytes", 4))) {
      SpeechSynthesis.SynthesisSession session =
          fixture
              .synthesis()
              .open(
                  new SpeechSynthesis.Request(
                      SpeechFixtureModule.FORMAT, "abcdefghij", 32, TIMEOUT));
      ByteArrayOutputStream collected = new ByteArrayOutputStream();
      int chunks = 0;
      byte[] chunk;
      while ((chunk = session.nextChunk()) != null) {
        assertThat(chunk.length).isLessThanOrEqualTo(4);
        collected.writeBytes(chunk);
        chunks++;
      }

      assertThat(chunks).isEqualTo(3);
      assertThat(new String(collected.toByteArray(), StandardCharsets.UTF_8))
          .isEqualTo("abcdefghij");
      assertThat(session.awaitCompletion().totalBytes()).isEqualTo(10);
      assertThat(session.awaitCompletion().formatKey()).isEqualTo(SpeechFixtureModule.FORMAT.key());
    }
  }
}
