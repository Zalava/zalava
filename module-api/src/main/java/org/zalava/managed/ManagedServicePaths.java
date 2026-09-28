package org.zalava.managed;

import java.nio.file.Path;
import java.util.Set;

final class ManagedServicePaths {

  private static final String ENGINE_SOCKET = "/var/run/docker.sock";

  private ManagedServicePaths() {}

  static Set<String> immutablePaths(Set<String> values, String name, boolean device) {
    Set<String> copied = values == null ? Set.of() : Set.copyOf(values);
    copied.forEach(value -> validate(value, name, device));
    return copied;
  }

  static void validate(String value, String name, boolean device) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not contain blank paths");
    }
    Path path = Path.of(value).normalize();
    if (!path.isAbsolute() || !path.toString().equals(value) || "/".equals(value)) {
      throw new IllegalArgumentException(name + " must contain canonical non-root absolute paths");
    }
    if (ENGINE_SOCKET.equals(value) || "/run/docker.sock".equals(value)) {
      throw new IllegalArgumentException(name + " must not grant an engine socket");
    }
    if (device && !value.startsWith("/dev/")) {
      throw new IllegalArgumentException("devices must be under /dev");
    }
  }
}
