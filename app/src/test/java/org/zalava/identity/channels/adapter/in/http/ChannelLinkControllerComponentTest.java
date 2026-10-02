package org.zalava.identity.channels.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;
import org.zalava.support.AuthenticatedSeaComponentTest;
import org.zalava.support.ComponentTestAccounts;

/** Full-context HTTP ownership and security boundary for channel links. */
@AuthenticatedSeaComponentTest
class ChannelLinkControllerComponentTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ComponentTestAccounts accounts;
  @Autowired private ChannelIdentityLinks links;

  @Test
  void issuesNarrowChallengeAndOnlyExposesOrRevokesTheOwnersLink() throws Exception {
    var owner = accounts.newActivated(AccountRole.MEMBER);
    var other = accounts.newActivated(AccountRole.MEMBER);
    var link =
        links.link(
            new org.zalava.identity.accounts.domain.Actor(owner.id()),
            new ExternalChannelIdentity("telegram", "12345"),
            ChannelOperationScope.of("chat:send"));

    mockMvc
        .perform(
            post("/api/channel-links/challenges")
                .with(accounts.authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"telegram\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").isString())
        .andExpect(jsonPath("$.expiresAt").exists());
    mockMvc
        .perform(get("/api/channel-links").with(accounts.authenticatedAs(other)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(get("/api/channel-links").with(accounts.authenticatedAs(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].subject").value("12345"))
        .andExpect(jsonPath("$[0].operations[0]").value("chat:send"));
    mockMvc
        .perform(delete("/api/channel-links/" + link.id()).with(accounts.authenticatedAs(other)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(delete("/api/channel-links/" + link.id()).with(accounts.authenticatedAs(owner)))
        .andExpect(status().isNoContent());
  }
}
