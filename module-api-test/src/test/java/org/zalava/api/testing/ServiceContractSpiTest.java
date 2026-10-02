package org.zalava.api.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.api.ZalavaServiceFactory;
import org.zalava.api.ZalavaServiceFactoryContext;
import org.zalava.api.extensions.content.ContentExtractionLimits;
import org.zalava.api.extensions.content.ContentExtractionOutcome;
import org.zalava.api.extensions.content.ContentExtractionRequest;
import org.zalava.api.extensions.content.ContentExtractionResult;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.api.extensions.content.ContentProcessor;
import org.zalava.api.extensions.content.ContentSourceInput;
import org.zalava.api.extensions.content.ContentSourceMetadata;

class ServiceContractSpiTest {
  private static final String MODULE_ID = "fixture-extractor-module";

  @Test
  void createsAndExercisesAContentExtractorServiceContract() {
    try (ServiceFixture fixture =
        ServiceFixture.create(new ExtractorModule(), ConfigFixture.empty())) {
      ContentExtractor extractor = fixture.service(ContentExtractor.CONTRACT);

      ContentExtractionRequest request =
          new ContentExtractionRequest(
              new ContentSourceMetadata("notes.txt", "text/plain", 5, "a".repeat(64)),
              ContentSourceInput.singleUse(
                  new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 1024),
              new ContentExtractionLimits(1024, 1024, 10, 100, 10));

      ContentExtractionOutcome outcome = extractor.extract(request);

      assertThat(outcome).isInstanceOf(ContentExtractionResult.class);
      assertThat(((ContentExtractionResult) outcome).text()).isEqualTo("hello");
      assertThat(((ContentExtractionResult) outcome).processor().id()).isEqualTo("fixture");
    }
  }

  private static final class ExtractorModule implements ZalavaModule {
    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor(MODULE_ID, "1.0.0", "Extractor fixture", "Extractor fixture");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of();
    }

    @Override
    public List<ZalavaServiceFactory<?>> serviceFactories() {
      return List.of(new ExtractorFactory());
    }
  }

  private static final class ExtractorFactory implements ZalavaServiceFactory<ContentExtractor> {
    @Override
    public ZalavaServiceDescriptor descriptor() {
      return new ZalavaServiceDescriptor("content-extractor", MODULE_ID, "1");
    }

    @Override
    public ZalavaServiceContract<ContentExtractor> contract() {
      return ContentExtractor.CONTRACT;
    }

    @Override
    public ContentExtractor create(ZalavaServiceFactoryContext context) {
      return request -> {
        try {
          String text =
              new String(request.input().openStream().readAllBytes(), StandardCharsets.UTF_8);
          return ContentExtractionResult.forRequest(
              request, new ContentProcessor("fixture", "1"), text, Map.of(), List.of());
        } catch (IOException exception) {
          throw new UncheckedIOException(exception);
        }
      };
    }
  }
}
