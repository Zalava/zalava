package org.zalava.discovery.adapter.out.springai;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;

public final class SeaToolIndex implements ToolIndex, Closeable {
  private static final int MAX_TOOL_SEARCH_RESULTS = 10;

  private final RegexToolIndex delegate = new RegexToolIndex();

  @Override
  public void indexTool(String sessionId, ToolReference toolReference) {
    requireText(sessionId, "Spring AI tool index sessionId must not be blank");
    if (toolReference != null && SeaToolCallbackNames.isSeaTool(toolReference.toolName())) {
      delegate.indexTool(sessionId, toolReference);
    }
  }

  @Override
  public void indexTools(String sessionId, List<ToolReference> toolReferences) {
    requireText(sessionId, "Spring AI tool index sessionId must not be blank");
    if (toolReferences == null) {
      throw new IllegalArgumentException("Spring AI tool references must not be null");
    }
    delegate.indexTools(
        sessionId,
        toolReferences.stream()
            .filter(
                reference ->
                    reference != null && SeaToolCallbackNames.isSeaTool(reference.toolName()))
            .toList());
  }

  @Override
  public ToolSearchResponse search(ToolSearchRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Spring AI tool search request must not be null");
    }
    requireText(request.sessionId(), "Spring AI tool search sessionId must not be blank");
    requireText(request.query(), "Spring AI tool search query must not be blank");
    if (request.categoryFilter() != null && !request.categoryFilter().isBlank()) {
      throw new IllegalArgumentException(
          "Spring AI regex tool search does not support categoryFilter");
    }
    if (request.maxResults() != null
        && (request.maxResults() < 1 || request.maxResults() > MAX_TOOL_SEARCH_RESULTS)) {
      throw new IllegalArgumentException(
          "Spring AI tool search maxResults must be between 1 and " + MAX_TOOL_SEARCH_RESULTS);
    }
    return delegate.search(request);
  }

  @Override
  public void clearIndex(String sessionId) {
    requireText(sessionId, "Spring AI tool index sessionId must not be blank");
    delegate.clearIndex(sessionId);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
