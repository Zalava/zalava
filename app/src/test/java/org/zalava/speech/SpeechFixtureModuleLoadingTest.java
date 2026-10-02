package org.zalava.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.api.extensions.speech.SpeechRecognition;
import org.zalava.api.extensions.speech.SpeechSynthesis;

class SpeechFixtureModuleLoadingTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @TempDir Path workspace;

  @Test
  void loadsSpeechServicesAndRunsARecognitionSynthesisRoundTrip() throws Exception {
    try (SpeechFixtureModule fixture =
        SpeechFixtureModule.start(
            workspace,
            SpeechFixtureModule.configuration(
                "transcript", "hello fixture", "chunkSizeBytes", 4))) {
      SpeechRecognition recognition = fixture.recognition();
      SpeechSynthesis synthesis = fixture.synthesis();

      SpeechRecognition.RecognitionSession recognitionSession =
          recognition.open(
              new SpeechRecognition.Request(Set.of(SpeechFixtureModule.FORMAT), 64, TIMEOUT));
      recognitionSession.acceptChunk(new byte[] {1, 2, 3});
      String transcript = recognitionSession.endInput().text();
      recognitionSession.close();
      assertThat(transcript).isEqualTo("hello fixture");

      SpeechSynthesis.SynthesisSession synthesisSession =
          synthesis.open(
              new SpeechSynthesis.Request(SpeechFixtureModule.FORMAT, transcript, 64, TIMEOUT));
      ByteArrayOutputStream audio = new ByteArrayOutputStream();
      byte[] chunk;
      while ((chunk = synthesisSession.nextChunk()) != null) {
        audio.writeBytes(chunk);
      }

      assertThat(new String(audio.toByteArray(), StandardCharsets.UTF_8))
          .isEqualTo("hello fixture");
      assertThat(synthesisSession.awaitCompletion().totalBytes())
          .isEqualTo("hello fixture".length());
      synthesisSession.close();
    }
  }

  @Test
  void createsProvidersWhenNoSecretIsConfigured() throws Exception {
    try (SpeechFixtureModule fixture =
        SpeechFixtureModule.start(workspace, Map.of("transcript", "hello fixture"))) {
      SpeechRecognition.RecognitionSession session =
          fixture
              .recognition()
              .open(new SpeechRecognition.Request(Set.of(SpeechFixtureModule.FORMAT), 16, TIMEOUT));
      session.acceptChunk(new byte[] {1});
      assertThat(session.endInput().text()).isEqualTo("hello fixture");
    }
  }
}
