package org.zalava.api.extensions.managed;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.zalava.api.ZalavaServiceContract;

/** Host-scoped access to the approved OCR worker for this module. */
@FunctionalInterface
public interface ManagedOcrService {
  ZalavaServiceContract<ManagedOcrService> CONTRACT =
      new ZalavaServiceContract<>("managed-ocr", "1", ManagedOcrService.class);

  String SERVICE_ID = "zalava-tika-ocr";

  /** Returns worker JSON, or empty if an approved worker is unavailable. */
  Optional<String> extract(Request request);

  record Request(String mediaType, String languages, byte[] document) {
    private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    private static final Set<String> MEDIA_TYPES =
        Set.of("application/pdf", "image/png", "image/jpeg");
    private static final Pattern LANGUAGES =
        Pattern.compile("(?:eng|spa|cat)(?:\\+(?:eng|spa|cat)){0,2}");

    public Request {
      Objects.requireNonNull(document, "document");
      if (document.length == 0 || document.length > MAX_DOCUMENT_BYTES) {
        throw new IllegalArgumentException("OCR document must contain between 1 byte and 10 MiB");
      }
      if (mediaType == null || !MEDIA_TYPES.contains(mediaType)) {
        throw new IllegalArgumentException("Unsupported OCR media type");
      }
      if (languages == null || !LANGUAGES.matcher(languages).matches()) {
        throw new IllegalArgumentException("Unsupported OCR languages");
      }
      document = document.clone();
    }

    @Override
    public byte[] document() {
      return document.clone();
    }
  }
}
