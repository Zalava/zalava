package org.zalava;

import org.springframework.boot.SpringApplication;
import org.testcontainers.utility.TestcontainersConfiguration;

public class TestSeaApplication {

  public static void main(String[] args) {
    SpringApplication.from(SeaApplication::main).with(TestcontainersConfiguration.class).run(args);
  }
}
