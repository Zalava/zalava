package org.zalava.identity.channels.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.application.port.out.ChannelIdentityLinkStore;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;

@SpringBootTest
@TestPropertySource(
    properties = {
      "jobrunr.background-job-server.enabled=false",
      "jobrunr.dashboard.enabled=false",
      "sea.accounts.security-enabled=false"
    })
class JdbcChannelIdentityLinkStoreIntegrationTest {
  @Autowired private AccountLifecycle accounts;
  @Autowired private ChannelIdentityLinks identities;
  @Autowired private ChannelIdentityLinkStore links;

  @Test
  void persistsAnActiveLinkAndRetainsItsRevocation() {
    var account =
        accounts.create(
            "channel-" + UUID.randomUUID(), "TemporaryPassword-123", AccountRole.MEMBER);
    var owner = new Actor(account.id());
    var identity = new ExternalChannelIdentity("telegram", "subject-" + UUID.randomUUID());

    var link = identities.link(owner, identity, ChannelOperationScope.of("chat:send"));

    assertThat(links.findActive(identity))
        .hasValueSatisfying(
            stored -> {
              assertThat(stored.id()).isEqualTo(link.id());
              assertThat(stored.identity()).isEqualTo(link.identity());
              assertThat(stored.owner()).isEqualTo(owner);
              assertThat(stored.scope()).isEqualTo(link.scope());
              assertThat(stored.revokedAt()).isNull();
            });
    assertThat(identities.resolve(identity, "chat:send")).contains(owner);

    identities.revoke(owner, link.id());

    assertThat(links.findById(link.id()))
        .hasValueSatisfying(value -> assertThat(value.revokedAt()).isNotNull());
    assertThat(links.findActive(identity)).isEmpty();
    assertThat(identities.resolve(identity, "chat:send")).isEmpty();
  }
}
