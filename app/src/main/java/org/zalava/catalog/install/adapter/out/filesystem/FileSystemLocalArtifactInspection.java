package org.zalava.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.LocalArtifactInspection;

public final class FileSystemLocalArtifactInspection implements LocalArtifactInspection {
  private final List<Path> trustedRoots;

  public FileSystemLocalArtifactInspection(List<Path> roots) {
    trustedRoots = roots.stream().map(FileSystemLocalArtifactInspection::realDirectory).toList();
  }

  @Override
  public InspectedArtifact inspect(String rawPath) {
    if (rawPath == null || rawPath.isBlank())
      throw new SourceModuleInstallationException("Local artifact path is required");
    try {
      Path artifact = Path.of(rawPath).toAbsolutePath().normalize();
      if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(artifact)
          || !artifact.getFileName().toString().endsWith(".jar"))
        throw new SourceModuleInstallationException("Local artifact must be a regular JAR file");
      Path real = artifact.toRealPath(LinkOption.NOFOLLOW_LINKS);
      if (trustedRoots.stream().noneMatch(real::startsWith))
        throw new SourceModuleInstallationException("Local artifact must be within a trusted root");
      return new InspectedArtifact(real.toString(), digest(real));
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to validate local artifact", ex);
    }
  }

  private static Path realDirectory(Path root) {
    try {
      Path directory = root.toAbsolutePath().normalize();
      Files.createDirectories(directory);
      return directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
    } catch (IOException ex) {
      throw new SourceModuleInstallationException(
          "Trusted local artifact root is not available: " + root, ex);
    }
  }

  private static String digest(Path artifact) {
    try {
      return "sha256:"
          + HexFormat.of()
              .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)));
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to read local artifact", ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
