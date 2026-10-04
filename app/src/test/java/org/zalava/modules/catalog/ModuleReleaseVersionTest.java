package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModuleReleaseVersionTest {
  @Test
  void ordersAlphaReleasesBeforeStableAndIgnoresBuildMetadata() {
    assertThat(ModuleReleaseVersion.compare("0.1.0-alpha.10", "0.1.0-alpha.4")).isPositive();
    assertThat(ModuleReleaseVersion.compare("0.1.0", "0.1.0-alpha.10")).isPositive();
    assertThat(ModuleReleaseVersion.compare("0.1.0-alpha.10", "0.1.0")).isNegative();
    assertThat(ModuleReleaseVersion.compare("0.1.0+build.2", "0.1.0+build.1")).isZero();
    assertThat(ModuleReleaseVersion.compare("0.1.0-alpha", "0.1.0-alpha.1")).isNegative();
    assertThat(ModuleReleaseVersion.compare("0.1.0-1", "0.1.0-alpha")).isNegative();
    assertThat(ModuleReleaseVersion.compare("0.1.0-alpha", "0.1.0-1")).isPositive();
    assertThat(ModuleReleaseVersion.compare("99999999999999999999.0.0", "1.0.0")).isPositive();
  }

  @Test
  void comparesNumericSegmentsNumericallyNotLexically() {
    assertThat(ModuleReleaseVersion.compare("1.10.0", "1.9.0")).isPositive();
    assertThat(ModuleReleaseVersion.compare("2.0.0", "10.0.0")).isNegative();
  }

  @Test
  void treatsMissingSegmentsAsZero() {
    assertThat(ModuleReleaseVersion.compare("1.0", "1.0.0")).isZero();
    assertThat(ModuleReleaseVersion.compare("1", "1.0.1")).isNegative();
  }

  @Test
  void reportsStrictlyNewerVersions() {
    assertThat(ModuleReleaseVersion.isNewer("1.1.0", "1.0.1")).isTrue();
    assertThat(ModuleReleaseVersion.isNewer("1.0.1", "1.0.1")).isFalse();
    assertThat(ModuleReleaseVersion.isNewer("1.0.0", "1.0.1")).isFalse();
  }
}
