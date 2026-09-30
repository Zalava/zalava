package org.zalava.channelidentity.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.channelidentity.application.port.out.ChannelIdentityLinkStore;
import org.zalava.channelidentity.domain.ChannelIdentityLink;
import org.zalava.channelidentity.domain.ChannelOperationScope;
import org.zalava.channelidentity.domain.ExternalChannelIdentity;

/** JDBC storage preserving revocation history while enforcing one active external identity. */
public final class JdbcChannelIdentityLinkStore implements ChannelIdentityLinkStore {
  private final JdbcClient jdbc;

  public JdbcChannelIdentityLinkStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ChannelIdentityLink> findActive(ExternalChannelIdentity identity) {
    return jdbc.sql("select * from sea_channel_identity_link where active_identity_key = :identity")
        .param("identity", activeKey(identity))
        .query(this::map)
        .optional();
  }

  @Override
  public Optional<ChannelIdentityLink> findById(UUID id) {
    return jdbc.sql("select * from sea_channel_identity_link where id = :id")
        .param("id", id)
        .query(this::map)
        .optional();
  }

  @Override
  public List<ChannelIdentityLink> findByOwner(Actor owner) {
    return jdbc.sql(
            "select * from sea_channel_identity_link where account_id = :account order by linked_at desc")
        .param("account", owner.accountId().value())
        .query(this::map)
        .list();
  }

  @Override
  public ChannelIdentityLink save(ChannelIdentityLink link) {
    if (findById(link.id()).isEmpty()) {
      jdbc.sql(
              """
              insert into sea_channel_identity_link
                  (id, channel_id, external_subject, active_identity_key, account_id, operations, linked_at, revoked_at)
              values (:id, :channel, :subject, :activeIdentity, :account, :operations, :linkedAt, :revokedAt)
              """)
          .param("id", link.id())
          .param("channel", link.identity().channel())
          .param("subject", link.identity().subject())
          .param("activeIdentity", link.revokedAt() == null ? activeKey(link.identity()) : null)
          .param("account", link.owner().accountId().value())
          .param("operations", String.join("\n", link.scope().operations()))
          .param("linkedAt", Timestamp.from(link.linkedAt()))
          .param("revokedAt", link.revokedAt() == null ? null : Timestamp.from(link.revokedAt()))
          .update();
      return link;
    }
    jdbc.sql(
            """
            update sea_channel_identity_link
            set active_identity_key = :activeIdentity, revoked_at = :revokedAt
            where id = :id
            """)
        .param("id", link.id())
        .param("activeIdentity", link.revokedAt() == null ? activeKey(link.identity()) : null)
        .param("revokedAt", link.revokedAt() == null ? null : Timestamp.from(link.revokedAt()))
        .update();
    return link;
  }

  private ChannelIdentityLink map(ResultSet row, int index) throws SQLException {
    return new ChannelIdentityLink(
        row.getObject("id", UUID.class),
        new ExternalChannelIdentity(row.getString("channel_id"), row.getString("external_subject")),
        new Actor(new AccountId(row.getObject("account_id", UUID.class))),
        new ChannelOperationScope(Set.copyOf(row.getString("operations").lines().toList())),
        row.getTimestamp("linked_at").toInstant(),
        row.getTimestamp("revoked_at") == null ? null : row.getTimestamp("revoked_at").toInstant());
  }

  private static String activeKey(ExternalChannelIdentity identity) {
    return identity.channel() + ':' + identity.subject();
  }
}
