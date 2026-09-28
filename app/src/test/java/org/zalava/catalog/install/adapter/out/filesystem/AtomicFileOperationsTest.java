package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileOperationsTest {

  @TempDir Path directory;

  @Test
  void failsClosedWhenAtomicReplacementIsUnsupported() throws Exception {
    Path target = directory.resolve("state.json");
    Path temporary = directory.resolve("state.json.tmp");
    Files.writeString(target, "previous");
    Files.writeString(temporary, "replacement");

    assertThatThrownBy(
            () ->
                AtomicFileOperations.replace(
                    temporary,
                    target,
                    (source, destination, options) -> {
                      throw new AtomicMoveNotSupportedException(
                          source.toString(), destination.toString(), "unsupported");
                    }))
        .isInstanceOf(AtomicMoveNotSupportedException.class);

    assertThat(Files.readString(target)).isEqualTo("previous");
    assertThat(Files.readString(temporary)).isEqualTo("replacement");
  }
}
