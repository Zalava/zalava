package org.zalava;

import org.springframework.boot.SpringApplication;
import org.testcontainers.utility.TestcontainersConfiguration;

public class TestZalavaApplication {

  public static void main(String[] args) {
    SpringApplication.from(ZalavaApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
