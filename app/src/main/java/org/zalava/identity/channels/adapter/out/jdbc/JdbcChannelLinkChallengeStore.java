package org.zalava.identity.channels.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.port.out.ChannelLinkChallengeStore;
import org.zalava.identity.channels.domain.ChannelLinkChallenge;
import org.zalava.identity.channels.domain.ChannelOperationScope;

public final class JdbcChannelLinkChallengeStore implements ChannelLinkChallengeStore {
  private final JdbcClient jdbc;

  public JdbcChannelLinkChallengeStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ChannelLinkChallenge> findActiveByCodeHash(String hash, Instant now) {
    return jdbc.sql(
            "select * from sea_channel_link_challenge where code_hash = :hash and consumed_at is null and expires_at > :now")
        .param("hash", hash)
        .param("now", Timestamp.from(now))
        .query(this::map)
        .optional();
  }

  @Override
  public ChannelLinkChallenge save(ChannelLinkChallenge value) {
    jdbc.sql(
            "insert into sea_channel_link_challenge (id, account_id, channel_id, operations, code_hash, expires_at, consumed_at) values (:id, :account, :channel, :operations, :hash, :expires, :consumed)")
        .param("id", value.id())
        .param("account", value.owner().accountId().value())
        .param("channel", value.channel())
        .param("operations", String.join("\n", value.scope().operations()))
        .param("hash", value.codeHash())
        .param("expires", Timestamp.from(value.expiresAt()))
        .param("consumed", null)
        .update();
    return value;
  }

  @Override
  public boolean consume(UUID id, Instant consumedAt) {
    return jdbc.sql(
                "update sea_channel_link_challenge set consumed_at = :consumed where id = :id and consumed_at is null and expires_at > :now")
            .param("id", id)
            .param("consumed", Timestamp.from(consumedAt))
            .param("now", Timestamp.from(consumedAt))
            .update()
        == 1;
  }

  private ChannelLinkChallenge map(ResultSet row, int ignored) throws SQLException {
    var consumed = row.getTimestamp("consumed_at");
    return new ChannelLinkChallenge(
        row.getObject("id", UUID.class),
        new Actor(new AccountId(row.getObject("account_id", UUID.class))),
        row.getString("channel_id"),
        new ChannelOperationScope(Set.copyOf(row.getString("operations").lines().toList())),
        row.getString("code_hash"),
        row.getTimestamp("expires_at").toInstant(),
        consumed == null ? null : consumed.toInstant());
  }
}
