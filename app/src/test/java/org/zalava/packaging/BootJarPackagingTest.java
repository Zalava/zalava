package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class BootJarPackagingTest {

  @Test
  void bootJarContainsExecutableApplicationMetadata() throws IOException {
    Path jar = Path.of(System.getProperty("sea.test.boot-jar"));
    String version = System.getProperty("sea.test.version");

    assertThat(jar).exists().hasFileName("sea-" + version + ".jar");
    assertThat(Files.size(jar)).isPositive();

    try (JarFile bootJar = new JarFile(jar.toFile())) {
      Attributes attributes = bootJar.getManifest().getMainAttributes();

      assertThat(attributes.getValue("Main-Class"))
          .isEqualTo("org.springframework.boot.loader.launch.JarLauncher");
      assertThat(attributes.getValue("Start-Class")).isEqualTo("org.zalava.SeaApplication");
      assertThat(bootJar.getEntry("BOOT-INF/classes/org/zalava/SeaApplication.class")).isNotNull();
      assertThat(bootJar.getEntry("BOOT-INF/lib/module-api-" + version + ".jar")).isNotNull();
    }
  }
}
