package org.zalava.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

class PostgreSqlTestDatabaseIsolationTest {
  @Test
  void isolatedUrlsDoNotSharePersistentDatabaseState() throws Exception {
    String firstUrl = PostgreSqlTestDatabase.isolatedJdbcUrl();
    String secondUrl = PostgreSqlTestDatabase.isolatedJdbcUrl();
    try (var first = DriverManager.getConnection(firstUrl, "test", "test");
        var second = DriverManager.getConnection(secondUrl, "test", "test");
        var firstStatement = first.createStatement();
        var secondStatement = second.createStatement()) {
      firstStatement.execute("CREATE TABLE isolated_test_marker (id integer)");
      try (var result =
          secondStatement.executeQuery("SELECT to_regclass('public.isolated_test_marker')")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isNull();
      }
    }
  }
}
