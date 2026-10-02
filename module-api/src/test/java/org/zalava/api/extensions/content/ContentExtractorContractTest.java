package org.zalava.api.extensions.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentExtractorContractTest {
  private static final ContentSourceMetadata SOURCE =
      new ContentSourceMetadata(
          "statement.pdf",
          "application/pdf",
          3,
          "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
  private static final ContentExtractionLimits LIMITS = new ContentExtractionLimits(3, 10, 2, 5, 1);
  private static final ContentProcessor PROCESSOR = new ContentProcessor("tika", "4.0.0");

  @Test
  void publishesAStableTypedServiceContract() {
    assertThat(ContentExtractor.CONTRACT.serviceId()).isEqualTo("content-extractor");
    assertThat(ContentExtractor.CONTRACT.contractVersion()).isEqualTo("1");
    assertThat(ContentExtractor.CONTRACT.serviceType()).isEqualTo(ContentExtractor.class);
  }

  @Test
  void exposesExactlyOneBoundedInputStreamWithoutPathOrReopenAccess() throws Exception {
    ContentSourceInput input =
        ContentSourceInput.singleUse(new ByteArrayInputStream(new byte[] {1, 2, 3}), 3);

    try (InputStream stream = input.openStream()) {
      assertThat(stream.readAllBytes()).containsExactly(1, 2, 3);
    }
    assertThat(input.maximumBytes()).isEqualTo(3);
    assertThatIllegalStateException().isThrownBy(input::openStream);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ContentSourceInput.singleUse(new ByteArrayInputStream(new byte[0]), 0));
  }

  @Test
  void failsClosedWhenTheSourceExceedsTheHostSelectedInputBound() throws Exception {
    ContentSourceInput input =
        ContentSourceInput.singleUse(new ByteArrayInputStream(new byte[] {1, 2}), 1);

    try (InputStream stream = input.openStream()) {
      assertThatThrownBy(stream::readAllBytes)
          .isInstanceOf(IOException.class)
          .hasMessage("source input exceeds maximumBytes");
    }
  }

  @Test
  void correlatesSuccessfulAndFailedOutcomesToTheOriginalRequest() {
    ContentExtractionRequest request = request(new byte[] {1, 2, 3});
    ContentExtractionResult result =
        ContentExtractionResult.forRequest(
            request,
            PROCESSOR,
            "hello",
            Map.of("title", List.of("hello")),
            List.of(new ContentLocation(0, 5, 1)));
    ContentExtractionFailure failure =
        ContentExtractionFailure.forRequest(
            request,
            PROCESSOR,
            ContentExtractionFailureCategory.MALFORMED_INPUT,
            "Malformed document");

    assertThat(result.source()).isEqualTo(SOURCE);
    assertThat(result.processor()).isEqualTo(PROCESSOR);
    assertThat(result.text()).isEqualTo("hello");
    assertThat(result.locations()).containsExactly(new ContentLocation(0, 5, 1));
    assertThat(failure.source()).isEqualTo(SOURCE);
    assertThat(failure.category()).isEqualTo(ContentExtractionFailureCategory.MALFORMED_INPUT);
  }

  @Test
  void normalizesMetadataAndDefensivelyCopiesAllNestedCollections() {
    List<String> title = new ArrayList<>(List.of("hello"));
    Map<String, List<String>> metadata = new HashMap<>(Map.of("title", title));
    List<ContentLocation> locations = new ArrayList<>(List.of(new ContentLocation(0, 5, null)));

    ContentExtractionResult result =
        ContentExtractionResult.forRequest(
            request(new byte[] {1, 2, 3}), PROCESSOR, "hello", metadata, locations);
    title.clear();
    metadata.clear();
    locations.clear();

    assertThat(result.metadata()).containsEntry("title", List.of("hello")).isUnmodifiable();
    assertThat(result.metadata().get("title")).containsExactly("hello").isUnmodifiable();
    assertThat(result.locations())
        .containsExactly(new ContentLocation(0, 5, null))
        .isUnmodifiable();
  }

  @Test
  void rejectsResultsThatExceedAnyRequestedLimitOrAreMalformed() {
    ContentExtractionRequest request = request(new byte[] {1, 2, 3});

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ContentExtractionResult.forRequest(
                    request, PROCESSOR, "more than ten", Map.of(), List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ContentExtractionResult.forRequest(
                    request,
                    PROCESSOR,
                    "text",
                    Map.of("one", List.of("1"), "two", List.of("2"), "three", List.of("3")),
                    List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ContentExtractionResult.forRequest(
                    request, PROCESSOR, "text", Map.of("title", List.of("sixsix")), List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ContentExtractionResult.forRequest(
                    request, PROCESSOR, "text", Map.of("", List.of("ok")), List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ContentExtractionResult.forRequest(
                    request,
                    PROCESSOR,
                    "text",
                    Map.of(),
                    List.of(new ContentLocation(0, 1, null), new ContentLocation(2, 3, null))));
  }

  @Test
  void validatesSourceMetadataRequestLimitsLocationsAndFailureDetails() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentSourceMetadata(" ", "text/plain", 0, SOURCE.sha256()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentSourceMetadata("name", " ", 0, SOURCE.sha256()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentSourceMetadata("name", "text/plain", -1, SOURCE.sha256()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentSourceMetadata("name", "text/plain", 0, "invalid"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentExtractionLimits(0, 1, 0, 1, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentExtractionLimits(1, 0, 0, 1, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentExtractionLimits(1, 1, -1, 1, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentExtractionLimits(1, 1, 0, 0, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContentExtractionLimits(1, 1, 0, 1, -1));
    assertThatIllegalArgumentException().isThrownBy(() -> new ContentLocation(-1, 0, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new ContentLocation(2, 1, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new ContentLocation(0, 1, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> new ContentProcessor("", "1"));
    assertThatIllegalArgumentException().isThrownBy(() -> new ContentProcessor("tika", ""));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ContentExtractionRequest(
                    SOURCE,
                    ContentSourceInput.singleUse(new ByteArrayInputStream(new byte[3]), 3),
                    new ContentExtractionLimits(2, 10, 1, 5, 0)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ContentExtractionFailure.forRequest(
                    request(new byte[] {1, 2, 3}),
                    PROCESSOR,
                    ContentExtractionFailureCategory.INTERNAL,
                    " "));
  }

  private static ContentExtractionRequest request(byte[] bytes) {
    return new ContentExtractionRequest(
        SOURCE, ContentSourceInput.singleUse(new ByteArrayInputStream(bytes), 3), LIMITS);
  }
}
