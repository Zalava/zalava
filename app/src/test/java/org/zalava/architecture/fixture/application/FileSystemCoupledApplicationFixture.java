package org.zalava.architecture.fixture.application;

import java.nio.file.Path;

public final class FileSystemCoupledApplicationFixture {

  private final Path path;

  public FileSystemCoupledApplicationFixture(Path path) {
    this.path = path;
  }

  public Path path() {
    return path;
  }
}
