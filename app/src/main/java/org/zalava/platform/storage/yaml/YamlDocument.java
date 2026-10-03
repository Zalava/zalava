package org.zalava.platform.storage.yaml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zalava's immutable representation of one persisted text document.
 *
 * <p>The parser owns the delimiter syntax. This type only protects callers from mutable metadata
 * crossing persistence boundaries.
 */
public record YamlDocument(Map<String, String> frontmatter, String body) {

  public YamlDocument {
    frontmatter =
        frontmatter == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(frontmatter));
    body = body == null ? "" : body;
  }
}
