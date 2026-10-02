package org.zalava.knowledge.adapter.in.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.content.ContentExtractionFailureCategory;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeEvidenceQueries;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.DerivationState;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.support.SecureSeaComponentTest;

/**
 * Full-context MockMvc component test for the SEA-owned knowledge lifecycle retention seam: an
 * OCR-derived source keeps its active derivation, citation identity and actor authorization when a
 * later bounded extraction fails, is shared, is revoked and is confirmed-deleted.
 */
@SecureSeaComponentTest
class KnowledgeExtractionRetentionComponentTest {
  @Autowired MockMvc mvc;
  @Autowired AccountLifecycle accounts;
  @Autowired KnowledgeSourceLifecycle lifecycle;
  @Autowired KnowledgeSourceStore sources;
  @Autowired KnowledgeExtractionRecordStore records;
  @Autowired KnowledgeDerivationStore derivations;
  @Autowired KnowledgeEvidenceQueries evidence;
  @Autowired ActorExecutionContext actors;

  @Test
  void failedOcrReprocessingRetainsTheActiveDerivationAndAuthorization() throws Exception {
    String login = "retention-" + UUID.randomUUID().toString().substring(0, 8);
    var account = accounts.create(login, "FixturePassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "FixturePassword-123", "ChangedFixture-123");
    Actor owner = new Actor(account.id());
    Actor reader = actor("retention-reader-");

    String marker = "retention" + UUID.randomUUID().toString().replace("-", "");
    String text = marker + " scanned receipt total 89 euros";
    String name = marker + "-receipt.txt";
    mvc.perform(
            multipart("/knowledge/upload")
                .file(
                    new MockMultipartFile(
                        "file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                .with(user(login).roles("MEMBER"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attributeExists("knowledgeMessage"));
    KnowledgeSource source =
        sources.visibleTo(owner).stream()
            .filter(value -> value.displayName().equals(name))
            .findFirst()
            .orElseThrow();

    KnowledgeDerivation first =
        lifecycle.beginReprocessing(owner, source.id(), "zalava-module-tika", "1");
    records.record(KnowledgeExtractionRecord.succeeded(first, text));
    lifecycle.completeReprocessing(owner, first, true);
    assertThat(lifecycle.requireOwned(owner, source.id()).processingState())
        .isEqualTo(SourceProcessingState.READY);

    KnowledgeDerivation failed =
        lifecycle.beginReprocessing(owner, source.id(), "zalava-module-tika", "1");
    records.record(
        KnowledgeExtractionRecord.failed(
            failed, ContentExtractionFailureCategory.UNAVAILABLE, "OCR worker is unavailable"));
    lifecycle.completeReprocessing(owner, failed, false);

    assertThat(lifecycle.requireOwned(owner, source.id()).processingState())
        .as("a failed reprocessing must retain the previous successful derivation")
        .isEqualTo(SourceProcessingState.READY);
    assertThat(derivations.active(source.id()))
        .get()
        .satisfies(
            active -> {
              assertThat(active.version()).isEqualTo(1);
              assertThat(active.state()).isEqualTo(DerivationState.ACTIVE);
            });
    assertThat(derivations.findBySourceId(source.id()))
        .filteredOn(derivation -> derivation.state() == DerivationState.FAILED)
        .extracting(KnowledgeDerivation::version)
        .containsExactly(2L);
    assertThat(citation(owner, source)).contains(text, "\"derivationVersion\":1");

    mvc.perform(
            post("/knowledge/" + source.id().value() + "/visibility")
                .param("shared", "true")
                .with(user(login).roles("MEMBER"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(citation(reader, source)).contains(text);

    mvc.perform(
            post("/knowledge/" + source.id().value() + "/visibility")
                .param("shared", "false")
                .with(user(login).roles("MEMBER"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(citation(reader, source)).contains("NOT_FOUND").doesNotContain(text);

    mvc.perform(
            post("/knowledge/" + source.id().value() + "/delete")
                .param("confirmed", "true")
                .with(user(login).roles("MEMBER"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(citation(owner, source)).contains("NOT_FOUND").doesNotContain(text);
  }

  private String citation(Actor actor, KnowledgeSource source) {
    return actors.call(
        actor,
        AccountRole.MEMBER,
        () ->
            evidence
                .source(actor, source.id().value().toString())
                .map(
                    value ->
                        "{\"status\":\"OK\",\"derivationVersion\":"
                            + value.derivationVersion()
                            + ",\"excerpt\":\""
                            + value.excerpt()
                            + "\"}")
                .orElse("{\"status\":\"NOT_FOUND\"}"));
  }

  private Actor actor(String prefix) {
    return new Actor(
        accounts
            .create(
                prefix + UUID.randomUUID().toString().substring(0, 8),
                "FixturePassword-123",
                AccountRole.MEMBER)
            .id());
  }
}
