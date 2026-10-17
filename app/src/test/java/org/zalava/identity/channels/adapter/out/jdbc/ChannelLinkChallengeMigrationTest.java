package org.zalava.identity.channels.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ChannelLinkChallengeMigrationTest {
  @Container
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"));

  @Test
  void upgradesExistingChallengesWithAnAccountIndexAndPreservesForeignKeyEnforcement()
      throws Exception {
    var migrations =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration/common", "classpath:db/migration/postgresql");
    migrations.target("10").load().migrate();
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var sql = connection.createStatement()) {
      sql.executeUpdate(
          """
          insert into zalava_account
            (id, login_name, password_hash, enabled, role, password_change_required, created_at, updated_at)
          select ('00000000-0000-0000-0000-00000000000' || n)::uuid,
                 'migration-' || n, 'test-hash', true, 'MEMBER', false, now(), now()
          from generate_series(1, 3) n
          """);
      sql.executeUpdate(
          """
          insert into zalava_channel_link_challenge
            (id, account_id, channel_id, operations, code_hash, expires_at, consumed_at)
          select gen_random_uuid(),
                 ('00000000-0000-0000-0000-00000000000' || case when n = 1 then 1 else 2 end)::uuid,
                 'telegram', 'chat:send', 'hash-' || n, now() - interval '1 day', now()
          from generate_series(1, 2000) n
          """);

      assertThat(migrations.target("11").load().migrate().migrationsExecuted).isEqualTo(1);

      try (var rows =
          sql.executeQuery(
              "select count(*) from zalava_channel_link_challenge where consumed_at is not null and expires_at < now()")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong(1)).isEqualTo(2000);
      }
      try (var rows =
          sql.executeQuery(
              """
              select i.indisvalid and i.indisready and i.indpred is null as usable,
                     pg_get_indexdef(i.indexrelid) as definition
              from pg_index i
              where i.indexrelid = 'zalava_channel_link_challenge_account_idx'::regclass
              """)) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getBoolean("usable")).isTrue();
        assertThat(rows.getString("definition")).contains("btree (account_id)");
      }
      sql.execute("analyze zalava_channel_link_challenge");
      try (var rows =
          sql.executeQuery(
              """
              explain select 1 from zalava_channel_link_challenge
              where account_id = '00000000-0000-0000-0000-000000000001'
              """)) {
        var plan = new StringBuilder();
        while (rows.next()) plan.append(rows.getString(1)).append('\n');
        assertThat(plan.toString()).contains("zalava_channel_link_challenge_account_idx");
      }
      assertThatThrownBy(
              () ->
                  sql.executeUpdate("delete from zalava_account where login_name = 'migration-1'"))
          .isInstanceOf(SQLException.class)
          .extracting(failure -> ((SQLException) failure).getSQLState())
          .isEqualTo("23503");
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "update zalava_account set id = gen_random_uuid() where login_name = 'migration-1'"))
          .isInstanceOf(SQLException.class)
          .extracting(failure -> ((SQLException) failure).getSQLState())
          .isEqualTo("23503");
      assertThat(sql.executeUpdate("delete from zalava_account where login_name = 'migration-3'"))
          .isEqualTo(1);
    }
  }
}
