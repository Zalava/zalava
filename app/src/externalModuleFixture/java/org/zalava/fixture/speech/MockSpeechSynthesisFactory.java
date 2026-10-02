package org.zalava.fixture.speech;

import java.util.Set;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.api.ZalavaServiceFactory;
import org.zalava.api.ZalavaServiceFactoryContext;
import org.zalava.api.extensions.speech.SpeechAudioFormat;
import org.zalava.api.extensions.speech.SpeechCapabilities;
import org.zalava.api.extensions.speech.SpeechSynthesis;

/** External fixture factory that creates the deterministic synthesis provider. */
public final class MockSpeechSynthesisFactory implements ZalavaServiceFactory<SpeechSynthesis> {
  static final SpeechAudioFormat FORMAT = new SpeechAudioFormat("wav", "pcm_s16le", 16000);
  private static final long DEFAULT_MAX_BYTES = 4096;
  private static final int DEFAULT_CHUNK_SIZE_BYTES = 4;

  @Override
  public ZalavaServiceDescriptor descriptor() {
    return new ZalavaServiceDescriptor(
        SpeechSynthesis.CONTRACT.serviceId(),
        MockSpeechConfiguration.MODULE_ID,
        SpeechSynthesis.CONTRACT.contractVersion());
  }

  @Override
  public ZalavaServiceContract<SpeechSynthesis> contract() {
    return SpeechSynthesis.CONTRACT;
  }

  @Override
  public SpeechSynthesis create(ZalavaServiceFactoryContext context) {
    MockSpeechConfiguration.requireSecret(context);
    long maxBytes =
        MockSpeechConfiguration.positiveLong(
            context.configuration(), MockSpeechConfiguration.MAX_BYTES, DEFAULT_MAX_BYTES);
    int chunkSizeBytes =
        MockSpeechConfiguration.positiveInt(
            context.configuration(),
            MockSpeechConfiguration.CHUNK_SIZE_BYTES,
            DEFAULT_CHUNK_SIZE_BYTES);
    return new MockSpeechSynthesis(
        new SpeechCapabilities(Set.of(FORMAT), true, maxBytes), chunkSizeBytes);
  }
}
