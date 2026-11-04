package org.zalava.web.control.application.port.in;

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
      long durationMillis,
      String conversationId,
      String inputPreview,
      String runId) {
    public Entry(
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
        long durationMillis,
        String conversationId,
        String inputPreview) {
      this(
          timestamp,
          providerId,
          toolName,
          actorId,
          confirmed,
          classification,
          policyTags,
          sideEffecting,
          success,
          errorType,
          errorMessage,
          resultPreview,
          durationMillis,
          conversationId,
          inputPreview,
          null);
    }

    public Entry(
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
        long durationMillis,
        String conversationId) {
      this(
          timestamp,
          providerId,
          toolName,
          actorId,
          confirmed,
          classification,
          policyTags,
          sideEffecting,
          success,
          errorType,
          errorMessage,
          resultPreview,
          durationMillis,
          conversationId,
          null);
    }

    public Entry(
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
        long durationMillis) {
      this(
          timestamp,
          providerId,
          toolName,
          actorId,
          confirmed,
          classification,
          policyTags,
          sideEffecting,
          success,
          errorType,
          errorMessage,
          resultPreview,
          durationMillis,
          "",
          null);
    }
  }
}
