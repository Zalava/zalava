package org.zalava.web.ui;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Bounded metadata-only observation of local socket examples; never opens a connection. */
final class LocalSocketExampleObservation {
  private static final int FILE_TYPE_MASK = 0170000;
  private static final int SOCKET_TYPE = 0140000;

  private LocalSocketExampleObservation() {}

  static String observe(String value) {
    return observe(value, Path.of("/run"));
  }

  static String observe(String value, Path trustedRoot) {
    if (value == null || value.length() > 4096 || !value.startsWith("unix:///")) return null;
    try {
      URI uri = URI.create(value);
      if (!"unix".equals(uri.getScheme())
          || uri.getRawAuthority() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || uri.getPath() == null) return null;
      Path path = Path.of(uri.getPath());
      if (!path.isAbsolute() || !path.equals(path.normalize()) || path.getFileName() == null)
        return null;
      if (trustedRoot.equals(Path.of("/run")) && path.startsWith(Path.of("/var/run"))) {
        path = trustedRoot.resolve(Path.of("/var/run").relativize(path));
      }
      if (!trustedRoot.equals(path.getParent())) return null;
      Path realRoot = trustedRoot.toRealPath();
      Path parent = path.getParent().toRealPath();
      if (!parent.startsWith(realRoot)) return null;
      Path target = parent.resolve(path.getFileName());
      Object mode = Files.getAttribute(target, "unix:mode", LinkOption.NOFOLLOW_LINKS);
      if (mode instanceof Integer bits && (bits & FILE_TYPE_MASK) == SOCKET_TYPE) {
        return "Local socket found in Zalava's environment; connection not tested.";
      }
      return "No local socket found at this example, or it is inaccessible.";
    } catch (IllegalArgumentException ex) {
      return null;
    } catch (UnsupportedOperationException ex) {
      return "Local socket detection is unavailable in Zalava’s environment; connection not tested.";
    } catch (IOException | SecurityException ex) {
      return "No local socket found at this example, or it is inaccessible.";
    }
  }
}
