package org.zalava.fixture;

import java.util.List;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.SeaModule;
import org.zalava.SeaServiceFactory;
import org.zalava.fixture.speech.MockSpeechRecognitionFactory;
import org.zalava.fixture.speech.MockSpeechSynthesisFactory;

public final class ExternalFixtureSeaModule implements SeaModule {

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
  public List<SeaServiceFactory<?>> serviceFactories() {
    return List.of(
        new ExternalFixtureContentExtractorFactory(),
        new MockSpeechRecognitionFactory(),
        new MockSpeechSynthesisFactory());
  }
}
