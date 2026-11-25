package org.zalava.capabilities.selfknowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CapabilityDomainTest {
  @Test
  void compatibilityRequiresAnExactKnownVersion() {
    assertThat(CapabilityCompatibility.matchesVersion("1.0", "1.0")).isTrue();
    assertThat(CapabilityCompatibility.matchesVersion("1.0", "2.0")).isFalse();
    assertThat(CapabilityCompatibility.matchesVersion("", "")).isFalse();
    assertThat(CapabilityCompatibility.matchesVersion(null, "1.0")).isFalse();
  }

  @Test
  void moduleIdentityRejectsMalformedExternalIds() {
    assertThat(CapabilityIdentity.module("weather-1"))
        .get()
        .extracting(CapabilityIdentity::value)
        .isEqualTo("module:weather-1");
    assertThat(CapabilityIdentity.module("../secret")).isEmpty();
    assertThat(CapabilityIdentity.module("bad\nname")).isEmpty();
    assertThatThrownBy(() -> new CapabilityIdentity("module:"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void metadataIsBoundedAndControlsAreRemoved() {
    assertThat(new CapabilityText(null).value()).isEmpty();
    assertThat(new CapabilityText(" x\ny ").value()).isEqualTo("x y");
    assertThat(new CapabilityText("x".repeat(600)).value()).hasSize(500);
  }

  @Test
  void searchIsBoundedCaseInsensitiveAndMatchesAcrossFields() {
    assertThat(new CapabilitySearch(" NOTES READ ", 1).matches("notes", "read")).isTrue();
    assertThat(new CapabilitySearch("unknown", 1).matches("notes", "read")).isFalse();
    assertThat(new CapabilitySearch("", 50).matches()).isTrue();
    assertThatThrownBy(() -> new CapabilitySearch("x", 51))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CapabilitySearch("x".repeat(201), 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
