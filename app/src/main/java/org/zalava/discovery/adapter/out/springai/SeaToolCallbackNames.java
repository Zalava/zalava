package org.zalava.discovery.adapter.out.springai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class SeaToolCallbackNames {

  private static final String PREFIX = "sea_";
  private static final int MAX_READABLE_LENGTH = 36;
  private static final Pattern SEA_TOOL_NAME = Pattern.compile("sea_[A-Za-z0-9_-]+_[0-9a-f]{16}");

  private SeaToolCallbackNames() {}

  public static String forTool(String providerId, String toolName) {
    requireText(providerId, "providerId");
    requireText(toolName, "toolName");
    String readableName = toolName.replaceAll("[^A-Za-z0-9_-]", "_");
    if (readableName.length() > MAX_READABLE_LENGTH) {
      readableName = readableName.substring(0, MAX_READABLE_LENGTH);
    }
    return PREFIX + readableName + "_" + hash(providerId + "\0" + toolName);
  }

  public static boolean isSeaTool(String toolName) {
    return toolName != null && SEA_TOOL_NAME.matcher(toolName).matches();
  }

  private static String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("SEA tool callback " + fieldName + " must not be blank");
    }
  }
}
