package org.zalava.fixture;

import java.util.List;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaServiceFactory;
import org.zalava.fixture.speech.MockSpeechRecognitionFactory;
import org.zalava.fixture.speech.MockSpeechSynthesisFactory;

public final class ExternalFixtureZalavaModule implements ZalavaModule {

  @Override
  public ModuleDescriptor descriptor() {
    return new ModuleDescriptor(
        "sea-external-module-fixture",
        "1.0.0",
        "External fixture",
        "Independently packaged SEA module used by runtime loading tests");
  }

  @Override
  public List<ProviderFactory> providerFactories() {
    return List.of(new ExternalFixtureProviderFactory());
  }

  @Override
  public List<ZalavaServiceFactory<?>> serviceFactories() {
    return List.of(
        new ExternalFixtureContentExtractorFactory(),
        new MockSpeechRecognitionFactory(),
        new MockSpeechSynthesisFactory());
  }
}
