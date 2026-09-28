package org.zalava.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpeechContractsTest {
  private static final SpeechAudioFormat PCM_16K = new SpeechAudioFormat("wav", "pcm_s16le", 16000);

  @Test
  void publishesTwoIndependentVersionedContracts() {
    assertThat(SpeechRecognition.CONTRACT.serviceId()).isEqualTo("speech-recognition");
    assertThat(SpeechRecognition.CONTRACT.contractVersion()).isEqualTo("1");
    assertThat(SpeechRecognition.CONTRACT.serviceType()).isEqualTo(SpeechRecognition.class);
    assertThat(SpeechSynthesis.CONTRACT.serviceId()).isEqualTo("speech-synthesis");
    assertThat(SpeechSynthesis.CONTRACT.contractVersion()).isEqualTo("1");
    assertThat(SpeechSynthesis.CONTRACT.serviceType()).isEqualTo(SpeechSynthesis.class);
  }

  @Test
  void validatesAudioFormatAndExposesACanonicalNegotiationKey() {
    assertThat(PCM_16K.key()).isEqualTo("wav/pcm_s16le/16000");
    assertThatIllegalArgumentException().isThrownBy(() -> new SpeechAudioFormat(" ", "pcm", 16000));
    assertThatIllegalArgumentException().isThrownBy(() -> new SpeechAudioFormat("wav", " ", 16000));
    assertThatIllegalArgumentException().isThrownBy(() -> new SpeechAudioFormat("wav", "pcm", 0));
    assertThatNullPointerException().isThrownBy(() -> new SpeechAudioFormat(null, "pcm", 16000));
  }

  @Test
  void negotiatesRequestedFormatsAgainstDeclaredCapabilities() {
    SpeechCapabilities capabilities = new SpeechCapabilities(Set.of(PCM_16K), true, 10);

    assertThat(capabilities.streaming()).isTrue();
    assertThat(capabilities.supports(null)).isTrue();
    assertThat(capabilities.supports(Set.of())).isTrue();
    assertThat(capabilities.supports(Set.of(PCM_16K))).isTrue();
    assertThat(capabilities.supports(Set.of(new SpeechAudioFormat("wav", "opus", 48000))))
        .isFalse();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SpeechCapabilities(Set.of(PCM_16K), true, 0));
    assertThat(new SpeechCapabilities(null, false, 1).formats()).isEmpty();
  }

  @Test
  void defensivelyCopiesDeclaredFormats() {
    Set<SpeechAudioFormat> formats = new HashSet<>();
    formats.add(PCM_16K);
    SpeechCapabilities capabilities = new SpeechCapabilities(formats, false, 1);

    formats.clear();

    assertThat(capabilities.formats()).containsExactly(PCM_16K);
  }

  @Test
  void exposesTypedFailureSubtypes() {
    assertThat(new SpeechException.ClosedSessionException("closed"))
        .isInstanceOf(SpeechException.class);
    assertThat(new SpeechException.UnsupportedFormatException("format"))
        .isInstanceOf(SpeechException.class);
    assertThat(new SpeechException.LimitExceededException("limit"))
        .isInstanceOf(SpeechException.class);
    assertThat(new SpeechException.DeadlineExceededException("deadline"))
        .isInstanceOf(SpeechException.class);
    assertThat(new SpeechException.CancelledException("cancelled"))
        .isInstanceOf(SpeechException.class);
    assertThat(new SpeechException.ConfigurationException("configuration"))
        .isInstanceOf(SpeechException.class);
    assertThat(new SpeechException("failure", new RuntimeException("cause")))
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  void validatesRecognitionRequestsAndTranscripts() {
    SpeechRecognition.Request request =
        new SpeechRecognition.Request(Set.of(PCM_16K), 10, Duration.ofSeconds(1));

    assertThat(request.formats()).containsExactly(PCM_16K);
    assertThat(request.maxBytes()).isEqualTo(10);
    assertThat(new SpeechRecognition.Request(null, 1, Duration.ofSeconds(1)).formats()).isEmpty();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SpeechRecognition.Request(Set.of(), 0, Duration.ofSeconds(1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SpeechRecognition.Request(Set.of(), 1, Duration.ZERO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SpeechRecognition.Request(Set.of(), 1, Duration.ofSeconds(-1)));
    assertThatNullPointerException()
        .isThrownBy(() -> new SpeechRecognition.Request(Set.of(), 1, null));

    assertThat(new SpeechRecognition.Transcript("  hello  ").text()).isEqualTo("hello");
    assertThatThrownBy(() -> new SpeechRecognition.Transcript(" "))
        .isInstanceOf(SpeechException.class);
    assertThatThrownBy(() -> new SpeechRecognition.Transcript(null))
        .isInstanceOf(SpeechException.class);
  }

  @Test
  void validatesSynthesisRequestsAndCompletion() {
    SpeechSynthesis.Request request =
        new SpeechSynthesis.Request(PCM_16K, "hello", 5, Duration.ofSeconds(1));

    assertThat(request.format()).isEqualTo(PCM_16K);
    assertThat(request.text()).isEqualTo("hello");
    assertThat(request.maxBytes()).isEqualTo(5);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SpeechSynthesis.Request(PCM_16K, " ", 5, Duration.ofSeconds(1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SpeechSynthesis.Request(PCM_16K, "hello", 0, Duration.ofSeconds(1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SpeechSynthesis.Request(PCM_16K, "hello", 5, Duration.ZERO));
    assertThatNullPointerException()
        .isThrownBy(() -> new SpeechSynthesis.Request(null, "hello", 5, Duration.ofSeconds(1)));
    assertThatNullPointerException()
        .isThrownBy(() -> new SpeechSynthesis.Request(PCM_16K, null, 5, Duration.ofSeconds(1)));

    assertThat(new SpeechSynthesis.Completion(PCM_16K.key(), 5).totalBytes()).isEqualTo(5);
    assertThatIllegalArgumentException().isThrownBy(() -> new SpeechSynthesis.Completion(" ", 5));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SpeechSynthesis.Completion(PCM_16K.key(), 0));
  }
}
