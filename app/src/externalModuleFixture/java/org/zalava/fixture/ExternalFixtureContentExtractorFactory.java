package org.zalava.fixture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.api.ZalavaServiceFactory;
import org.zalava.api.ZalavaServiceFactoryContext;
import org.zalava.api.extensions.content.ContentExtractionFailure;
import org.zalava.api.extensions.content.ContentExtractionFailureCategory;
import org.zalava.api.extensions.content.ContentExtractionResult;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.api.extensions.content.ContentProcessor;

/** Minimal independently compiled extractor used to verify the stable service boundary. */
final class ExternalFixtureContentExtractorFactory
    implements ZalavaServiceFactory<ContentExtractor> {
  private static final ContentProcessor PROCESSOR = new ContentProcessor("fixture", "1.0.0");

  @Override
  public ZalavaServiceDescriptor descriptor() {
    return new ZalavaServiceDescriptor(
        ContentExtractor.CONTRACT.serviceId(),
        "zalava-external-module-fixture",
        ContentExtractor.CONTRACT.contractVersion());
  }

  @Override
  public org.zalava.api.ZalavaServiceContract<ContentExtractor> contract() {
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
