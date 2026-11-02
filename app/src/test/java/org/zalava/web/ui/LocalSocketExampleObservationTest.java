package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSocketExampleObservationTest {
  @TempDir Path root;

  @Test
  void findsAnAllowedSocketWithoutOpeningAConnection() throws Exception {
    Path socket = root.resolve("engine.sock");
    try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
      server.bind(UnixDomainSocketAddress.of(socket));
      server.configureBlocking(false);
      assertThat(LocalSocketExampleObservation.observe("unix://" + socket, root))
          .isEqualTo("Local socket found in Zalava's environment; connection not tested.");
      assertThat(server.accept()).isNull();
    }
  }

  @Test
  void reportsMissingOrNonSocketPathsWithoutClaimingAConnection() throws Exception {
    Path missing = root.resolve("missing.sock");
    Path regular = Files.createFile(root.resolve("regular.sock"));
    assertThat(LocalSocketExampleObservation.observe("unix://" + missing, root))
        .contains("No local socket found");
    assertThat(LocalSocketExampleObservation.observe("unix://" + regular, root))
        .contains("No local socket found");
  }

  @Test
  void ignoresRemoteAndOutOfScopePaths() {
    assertThat(LocalSocketExampleObservation.observe("https://example.test", root)).isNull();
    assertThat(LocalSocketExampleObservation.observe("unix:///%00", root)).isNull();
    assertThat(LocalSocketExampleObservation.observe("unix:///etc/example.sock", root)).isNull();
    assertThat(LocalSocketExampleObservation.observe("unix://host/run/example.sock", root))
        .isNull();
    assertThat(LocalSocketExampleObservation.observe("unix:///run/../etc/example.sock", root))
        .isNull();
  }

  @Test
  void ignoresNestedSocketPaths() throws Exception {
    Path nested = Files.createDirectory(root.resolve("nested"));
    assertThat(
            LocalSocketExampleObservation.observe("unix://" + nested.resolve("engine.sock"), root))
        .isNull();
  }

  @Test
  void doesNotFollowAParentSymlinkOutsideTheTrustedRoot(@TempDir Path outside) throws Exception {
    Files.createSymbolicLink(root.resolve("linked"), outside);
    assertThat(
            LocalSocketExampleObservation.observe("unix://" + root.resolve("linked/socket"), root))
        .isNull();
  }

  @Test
  void doesNotFollowASecondSocketPathThroughAFinalSymlink() throws Exception {
    Path socket = root.resolve("engine.sock");
    try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
      server.bind(UnixDomainSocketAddress.of(socket));
      Path alias = Files.createSymbolicLink(root.resolve("alias.sock"), socket);
      assertThat(LocalSocketExampleObservation.observe("unix://" + alias, root))
          .contains("No local socket found");
    }
  }
}
