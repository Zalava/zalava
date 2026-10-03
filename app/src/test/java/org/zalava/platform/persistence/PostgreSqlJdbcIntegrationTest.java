package org.zalava.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("postgresql")
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlJdbcIntegrationTest {

  @Container
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
          .withDatabaseName("zalava_test")
          .withUsername("zalava_test")
          .withPassword("test-only-password");

  @Test
  void connectsWithThePostgreSqlDriverUsingExternallySuppliedDatasourceSettings() {
    HikariConfig configuration = new HikariConfig();
    configuration.setDriverClassName("org.postgresql.Driver");
    configuration.setJdbcUrl(postgres.getJdbcUrl());
    configuration.setUsername(postgres.getUsername());
    configuration.setPassword(postgres.getPassword());
    HikariDataSource dataSource = new HikariDataSource(configuration);
    try {
      String databaseName =
          JdbcClient.create(dataSource)
              .sql("select current_database()")
              .query(String.class)
              .single();

      assertThat(databaseName).isEqualTo("zalava_test");
    } finally {
      dataSource.close();
    }
  }
}
