package org.zalava.control.application.port.in;

import java.util.List;

public interface InvocationLogQueries {
  List<Entry> recentEntries();

  record Entry(
      String timestamp,
      String providerId,
      String toolName,
      String actorId,
      boolean confirmed,
      String classification,
      List<String> policyTags,
      boolean sideEffecting,
      boolean success,
      String errorType,
      String errorMessage,
      String resultPreview,
      long durationMillis) {}
}
