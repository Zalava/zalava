package org.zalava.catalog.install.acceptance;

import java.util.ArrayList;
import java.util.List;

/** Resolves the opt-in indexed-install acceptance credentials from properties or environment. */
final class IndexedInstallCredentials {

  private static final String ZALAVA_PUBLISH_TOKEN = "zalava.indexed-install.publish-token";
  private static final String GITHUB_PACKAGES_USERNAME =
      "sea.indexed-install.github-packages-username";
  private static final String GITHUB_PACKAGES_TOKEN = "sea.indexed-install.github-packages-token";
  private static final String MODULE_LOCATOR_URL = "sea.indexed-install.module-locator-url";

  private IndexedInstallCredentials() {}

  static String githubToken() {
    return value(ZALAVA_PUBLISH_TOKEN, "ZALAVA_INDEXED_INSTALL_PUBLISH_TOKEN");
  }

  static String packagesUsername() {
    return value(GITHUB_PACKAGES_USERNAME, "SEA_INDEXED_INSTALL_GITHUB_PACKAGES_USERNAME");
  }

  static String packagesToken() {
    return value(GITHUB_PACKAGES_TOKEN, "SEA_INDEXED_INSTALL_GITHUB_PACKAGES_TOKEN");
  }

  static String moduleLocatorUrl() {
    return value(MODULE_LOCATOR_URL, "SEA_INDEXED_INSTALL_MODULE_LOCATOR_URL");
  }

  static void requireAvailable() {
    List<String> missing = new ArrayList<>();
    if (githubToken().isBlank()) missing.add(ZALAVA_PUBLISH_TOKEN);
    if (packagesUsername().isBlank()) missing.add(GITHUB_PACKAGES_USERNAME);
    if (packagesToken().isBlank()) missing.add(GITHUB_PACKAGES_TOKEN);
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Indexed install acceptance requires scoped credentials: " + String.join(", ", missing));
    }
  }

  private static String value(String systemProperty, String environmentVariable) {
    String configured = System.getProperty(systemProperty);
    if (configured == null || configured.isBlank()) {
      configured = System.getenv(environmentVariable);
    }
    return configured == null ? "" : configured.strip();
  }
}
