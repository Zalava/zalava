package org.zalava.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaServiceContract;
import org.zalava.ZalavaServiceDescriptor;
import org.zalava.ZalavaServiceFactory;
import org.zalava.ZalavaServiceFactoryContext;
import org.zalava.content.ContentExtractionLimits;
import org.zalava.content.ContentExtractionOutcome;
import org.zalava.content.ContentExtractionRequest;
import org.zalava.content.ContentExtractionResult;
import org.zalava.content.ContentExtractor;
import org.zalava.content.ContentProcessor;
import org.zalava.content.ContentSourceInput;
import org.zalava.content.ContentSourceMetadata;

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
