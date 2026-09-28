package org.zalava.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.support.SecureSeaComponentTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SecureSeaComponentTest
class KnowledgeControllerComponentTest {
  private static final String MEMBER_LOGIN = "knowledge-member";
  private static final String SOURCE_HASH = "f".repeat(64);

  @Autowired MockMvc mockMvc;
  @Autowired AccountLifecycle accounts;
  @Autowired KnowledgeSourceStore sources;
  private Account member;

  @BeforeEach
  void prepareMember() {
    member =
        accounts
            .findByLoginName(MEMBER_LOGIN)
            .orElseGet(
                () -> accounts.create(MEMBER_LOGIN, "MemberPassword-123", AccountRole.MEMBER));
    if (member.passwordChangeRequired()) {
      accounts.changePassword(member.id(), "MemberPassword-123", "ChangedMemberPassword-123");
      member = accounts.findByLoginName(MEMBER_LOGIN).orElseThrow();
    }
  }

  @Test
  void requiresAnAuthenticatedMemberAndCsrfForMutations() throws Exception {
    mockMvc.perform(get("/knowledge")).andExpect(status().is3xxRedirection());
    mockMvc
        .perform(post("/knowledge/not-a-source/visibility").with(asMember()))
        .andExpect(status().isForbidden());
  }

  @Test
  void rendersMemberSearchWithoutLeakingSensitiveFields() throws Exception {
    mockMvc
        .perform(get("/knowledge").param("query", "renewal").with(asMember()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>SEA Knowledge</title>")))
        .andExpect(content().string(containsString("No sources.")))
        .andExpect(content().string(not(containsString("sha256"))));
  }

  @Test
  void ownerInspectsSafeSourceProvenanceWithoutSensitiveFields() throws Exception {
    KnowledgeSource source =
        sources.register(source(new Actor(member.id()), KnowledgeVisibility.PRIVATE));

    mockMvc
        .perform(get("/knowledge/" + source.id().value()).with(asMember()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>SEA Knowledge Source</title>")))
        .andExpect(content().string(containsString("household-notes.txt")))
        .andExpect(content().string(containsString("text/plain")))
        .andExpect(content().string(containsString("19 bytes")))
        .andExpect(content().string(containsString("READY")))
        .andExpect(content().string(not(containsString(SOURCE_HASH))))
        .andExpect(content().string(not(containsString(member.id().value().toString()))));
  }

  @Test
  void sharedSourceInspectionIsRevokedImmediatelyAfterUnshare() throws Exception {
    Account owner = account("knowledge-detail-owner");
    KnowledgeSource shared =
        sources.register(source(new Actor(owner.id()), KnowledgeVisibility.GROUP_SHARED));

    mockMvc
        .perform(get("/knowledge/" + shared.id().value()).with(asMember()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("household-notes.txt")));

    sources.save(shared.unshare(Instant.parse("2026-09-09T20:00:00Z")));

    mockMvc
        .perform(get("/knowledge/" + shared.id().value()).with(asMember()))
        .andExpect(status().isNotFound());
  }

  @Test
  void privateAndMalformedSourceIdsHaveTheSameUnavailableResponse() throws Exception {
    Account owner = account("knowledge-private-owner");
    KnowledgeSource privateSource =
        sources.register(source(new Actor(owner.id()), KnowledgeVisibility.PRIVATE));

    mockMvc
        .perform(get("/knowledge/" + privateSource.id().value()).with(asMember()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/knowledge/not-a-source").with(asMember()))
        .andExpect(status().isNotFound());
  }

  private Account account(String login) {
    Account account =
        accounts
            .findByLoginName(login)
            .orElseGet(() -> accounts.create(login, "MemberPassword-123", AccountRole.MEMBER));
    if (account.passwordChangeRequired()) {
      accounts.changePassword(account.id(), "MemberPassword-123", "ChangedMemberPassword-123");
      return accounts.findByLoginName(login).orElseThrow();
    }
    return account;
  }

  private static RequestPostProcessor asMember() {
    return user(MEMBER_LOGIN).roles("MEMBER");
  }

  private static KnowledgeSource source(Actor owner, KnowledgeVisibility visibility) {
    Instant created = Instant.parse("2026-09-09T19:00:00Z");
    return new KnowledgeSource(
        KnowledgeSourceId.create(),
        owner,
        "household-notes.txt",
        "text/plain",
        19,
        SOURCE_HASH,
        visibility,
        SourceProcessingState.READY,
        created,
        created,
        0);
  }
}
