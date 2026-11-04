package org.zalava.web.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Stable HTML field names for permission keys. */
public final class PermissionFields {
  private PermissionFields() {}

  public static String field(String kind, String module, String provider, String name) {
    return "p." + kind + "." + encode(module) + "." + encode(provider) + "." + encode(name);
  }

  public static boolean selected(
      Map<String, String> submitted, String field, String original, String choice) {
    return submitted.getOrDefault(field, original).equals(choice);
  }

  private static String encode(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
