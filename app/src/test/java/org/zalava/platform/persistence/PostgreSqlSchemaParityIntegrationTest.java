package org.zalava.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;

@Tag("postgresql")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlSchemaParityIntegrationTest {

  @Container
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
          .withDatabaseName("zalava_schema_parity")
          .withUsername("zalava_test")
          .withPassword("test-only-password");

  @Autowired private JdbcClient jdbc;
  @Autowired private AccountLifecycle accounts;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("zalava.accounts.security-enabled", () -> "false");
    registry.add("zalava.accounts.bootstrap-login", () -> "postgres-admin");
    registry.add("zalava.accounts.bootstrap-password", () -> "PostgresPassword-123");
  }

  @Test
  void startsFreshPostgreSqlWithFlywayZalavaAndJobRunrPersistence() {
    assertThat(tableExists("flyway_schema_history")).isTrue();
    assertThat(tableExists("zalava_account")).isTrue();
    assertThat(tableExists("knowledge_source")).isTrue();
    assertThat(tableExists("knowledge_derivation")).isTrue();
    assertThat(tableExists("knowledge_extraction_record")).isTrue();
    assertThat(tableExists("jobrunr_jobs")).isTrue();

    accounts.create("postgres-member", "PostgresPassword-123", AccountRole.MEMBER);

    assertThat(accounts.findByLoginName("postgres-member")).isPresent();
    assertThat(
            jdbc.sql("select count(*) from flyway_schema_history where success = true")
                .query(Long.class)
                .single())
        .isEqualTo(6L);
  }

  private boolean tableExists(String name) {
    return jdbc.sql(
            """
                select exists (
                  select 1 from information_schema.tables
                  where table_schema = current_schema() and table_name = :name
                )
                """)
        .param("name", name)
        .query(Boolean.class)
        .single();
  }
}
