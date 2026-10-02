package org.zalava.modules.catalog.install.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JdkCuratedMavenArtifactResolverTest {
  @Test
  void encodesRepositoryCredentialsAsBasicAuthorization() {
    assertThat(
            new JdkCuratedMavenArtifactResolver.Credentials("octocat", "package-token")
                .basicAuthorization())
        .isEqualTo("Basic b2N0b2NhdDpwYWNrYWdlLXRva2Vu");
  }

  @Test
  void rejectsPartialRepositoryCredentials() {
    assertThatThrownBy(() -> new JdkCuratedMavenArtifactResolver.Credentials("octocat", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("repository credentials require username and token");
  }
}
