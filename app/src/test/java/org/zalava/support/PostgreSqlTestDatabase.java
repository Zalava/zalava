package org.zalava.support;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class PostgreSqlTestDatabase {

  private static final String JDBC_URL =
      "jdbc:tc:postgresql:18.4-alpine:///zalava_test?TC_DAEMON=true";

  private PostgreSqlTestDatabase() {}

  public static void register(DynamicPropertyRegistry registry) {
    String jdbcUrl = newJdbcUrl();
    registry.add("spring.datasource.url", () -> jdbcUrl);
  }

  public static String jdbcUrl() {
    return JDBC_URL;
  }

  public static String newJdbcUrl() {
    return JDBC_URL;
  }

  public static String isolatedJdbcUrl() {
    return "jdbc:tc:postgresql:18.4-alpine:///zalava_"
        + UUID.randomUUID().toString().replace("-", "")
        + "?TC_DAEMON=true";
  }
}
