package org.zalava.fixture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.zalava.ZalavaServiceDescriptor;
import org.zalava.ZalavaServiceFactory;
import org.zalava.ZalavaServiceFactoryContext;
import org.zalava.content.ContentExtractionFailure;
import org.zalava.content.ContentExtractionFailureCategory;
import org.zalava.content.ContentExtractionResult;
import org.zalava.content.ContentExtractor;
import org.zalava.content.ContentProcessor;

/** Minimal independently compiled extractor used to verify the stable service boundary. */
final class ExternalFixtureContentExtractorFactory
    implements ZalavaServiceFactory<ContentExtractor> {
  private static final ContentProcessor PROCESSOR = new ContentProcessor("fixture", "1.0.0");

  @Override
  public ZalavaServiceDescriptor descriptor() {
    return new ZalavaServiceDescriptor(
        ContentExtractor.CONTRACT.serviceId(),
        "sea-external-module-fixture",
        ContentExtractor.CONTRACT.contractVersion());
  }

  @Override
  public org.zalava.ZalavaServiceContract<ContentExtractor> contract() {
    return ContentExtractor.CONTRACT;
  }

  @Override
  public ContentExtractor create(ZalavaServiceFactoryContext context) {
    return request -> {
      try {
        String text =
            new String(request.input().openStream().readAllBytes(), StandardCharsets.UTF_8);
        return ContentExtractionResult.forRequest(request, PROCESSOR, text, Map.of(), List.of());
      } catch (IOException | IllegalStateException exception) {
        return ContentExtractionFailure.forRequest(
            request,
            PROCESSOR,
            ContentExtractionFailureCategory.MALFORMED_INPUT,
            "Fixture source could not be read");
      }
    };
  }
}
