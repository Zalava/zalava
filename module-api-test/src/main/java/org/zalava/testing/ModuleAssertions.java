package org.zalava.testing;

import java.util.Objects;
import org.assertj.core.api.Assertions;
import org.zalava.ZalavaOperationResult;
import tools.jackson.databind.JsonNode;

/** Assertion helpers for module-owned contract tests. */
public final class ModuleAssertions {

  private ModuleAssertions() {}

  /** Invokes a tool and asserts it reported success, returning its content. */
  public static Object assertToolSucceeds(
      ProviderFixture providers, String providerId, String toolName, JsonNode arguments) {
    ZalavaOperationResult result = invoke(providers, providerId, toolName, arguments);
    Assertions.assertThat(result.success())
        .as("tool %s.%s should succeed", providerId, toolName)
        .isTrue();
    return result.content();
  }

  /** Invokes a tool and asserts it reported a logical failure, returning its content. */
  public static Object assertToolFails(
      ProviderFixture providers, String providerId, String toolName, JsonNode arguments) {
    ZalavaOperationResult result = invoke(providers, providerId, toolName, arguments);
    Assertions.assertThat(result.success())
        .as("tool %s.%s should fail", providerId, toolName)
        .isFalse();
    return result.content();
  }

  private static ZalavaOperationResult invoke(
      ProviderFixture providers, String providerId, String toolName, JsonNode arguments) {
    Objects.requireNonNull(providers, "providers");
    return providers.invoke(providerId, toolName, arguments);
  }
}
