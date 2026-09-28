package org.zalava.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AtomicFileOperations {

  private AtomicFileOperations() {}

  static void replace(Path temporary, Path target) throws IOException {
    replace(temporary, target, Files::move);
  }

  static void replace(Path temporary, Path target, MoveOperation move) throws IOException {
    move.move(
        temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  @FunctionalInterface
  interface MoveOperation {
    Path move(Path source, Path target, CopyOption... options) throws IOException;
  }
}
