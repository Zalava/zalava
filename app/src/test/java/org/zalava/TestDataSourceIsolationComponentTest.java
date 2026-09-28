package org.zalava;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.zalava.support.SeaComponentTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SeaComponentTest
class TestDataSourceIsolationComponentTest {

  @Autowired private DataSource dataSource;

  @Test
  void usesTheSharedPostgreSqlComponentDatasource() {
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);

    HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
    assertThat(hikariDataSource.getJdbcUrl()).startsWith("jdbc:tc:postgresql:");
  }
}
