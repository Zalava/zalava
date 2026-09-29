package org.zalava.fixture.speech;

import java.util.Set;
import org.zalava.ZalavaServiceContract;
import org.zalava.ZalavaServiceDescriptor;
import org.zalava.ZalavaServiceFactory;
import org.zalava.ZalavaServiceFactoryContext;
import org.zalava.speech.SpeechAudioFormat;
import org.zalava.speech.SpeechCapabilities;
import org.zalava.speech.SpeechRecognition;

/** External fixture factory that creates the deterministic recognition provider. */
public final class MockSpeechRecognitionFactory implements ZalavaServiceFactory<SpeechRecognition> {
  static final SpeechAudioFormat FORMAT = new SpeechAudioFormat("wav", "pcm_s16le", 16000);
  private static final long DEFAULT_MAX_BYTES = 4096;
  private static final String DEFAULT_TRANSCRIPT = "fixture transcript";

  @Override
  public ZalavaServiceDescriptor descriptor() {
    return new ZalavaServiceDescriptor(
        SpeechRecognition.CONTRACT.serviceId(),
        MockSpeechConfiguration.MODULE_ID,
        SpeechRecognition.CONTRACT.contractVersion());
  }

  @Override
  public ZalavaServiceContract<SpeechRecognition> contract() {
    return SpeechRecognition.CONTRACT;
  }

  @Override
  public SpeechRecognition create(ZalavaServiceFactoryContext context) {
    MockSpeechConfiguration.requireSecret(context);
    long maxBytes =
        MockSpeechConfiguration.positiveLong(
            context.configuration(), MockSpeechConfiguration.MAX_BYTES, DEFAULT_MAX_BYTES);
    String transcript =
        MockSpeechConfiguration.text(
            context.configuration(), MockSpeechConfiguration.TRANSCRIPT, DEFAULT_TRANSCRIPT);
    return new MockSpeechRecognition(
        new SpeechCapabilities(Set.of(FORMAT), true, maxBytes), transcript);
  }
}
