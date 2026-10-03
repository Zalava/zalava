package org.zalava.knowledge.adapter.in.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.assistant.agent.AgentRequestTools;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.support.SecureZalavaComponentTest;

@SecureZalavaComponentTest
class KnowledgeEvidenceComponentTest {
  @Autowired MockMvc mvc;
  @Autowired AccountLifecycle accounts;
  @Autowired KnowledgeSourceLifecycle lifecycle;
  @Autowired KnowledgeSourceStore sources;
  @Autowired KnowledgeExtractionRecordStore records;
  @Autowired ActorExecutionContext actors;
  @Autowired AgentRequestTools selection;
  @Autowired org.zalava.assistant.agent.application.port.in.AgentExecution agent;
  @Autowired org.zalava.web.control.application.port.in.InvocationLogQueries audit;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  org.zalava.assistant.agent.application.port.out.AgentModel model;

  @Test
  void uploadedHouseholdEvidenceIsCitedAndSharingRevocationIsImmediate() throws Exception {
    String login = "evidence-" + UUID.randomUUID().toString().substring(0, 8);
    var account = accounts.create(login, "FixturePassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "FixturePassword-123", "ChangedFixture-123");
    Actor owner = new Actor(account.id());
    Actor reader = actor();
    String marker = "evidence" + UUID.randomUUID().toString().replace("-", "");
    String[][] documents = {
      {"invoice.txt", "Invoice INV-2026-104 total 89 euros."},
      {"warranty.txt", "Washing machine warranty expires 2028-05-31."},
      {"manual.txt", "Router reset: hold the rear button for ten seconds."},
      {"school.txt", "School trip departs 2026-10-14 at 08:30."},
      {"reservation.txt", "Hotel reservation ABC-784 check-in 2026-12-20."}
    };
    var tools = tools();
    for (var document : documents) {
      String name = marker + "-" + document[0];
      String text = marker + " " + document[1];
      mvc.perform(
              multipart("/knowledge/upload")
                  .file(
                      new MockMultipartFile(
                          "file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                  .with(user(login).roles("MEMBER")))
          .andExpect(status().isForbidden());
      mvc.perform(
              multipart("/knowledge/upload")
                  .file(
                      new MockMultipartFile(
                          "file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                  .with(user(login).roles("MEMBER"))
                  .with(csrf()))
          .andExpect(status().is3xxRedirection());
      var source =
          sources.visibleTo(owner).stream()
              .filter(value -> value.displayName().equals(name))
              .findFirst()
              .orElseThrow();
      var candidate =
          lifecycle.beginReprocessing(owner, source.id(), "fixture-digital-extractor", "1");
      records.record(KnowledgeExtractionRecord.succeeded(candidate, text));
      lifecycle.completeReprocessing(owner, candidate, true);
      String question = "What does my " + document[0].replace(".txt", "") + " say?";
      org.mockito.Mockito.doAnswer(
              invocation -> {
                assertThat((String) invocation.getArgument(1)).contains(question);
                java.util.List<Object> callbacks = invocation.getArgument(2);
                var callback =
                    callbacks.stream()
                        .flatMap(
                            value -> {
                              if (value instanceof org.springframework.ai.tool.ToolCallback tool)
                                return java.util.stream.Stream.of(tool);
                              return java.util.Arrays.stream(
                                  ((org.springframework.ai.tool.ToolCallbackProvider) value)
                                      .getToolCallbacks());
                            })
                        .filter(
                            value -> value.getToolDefinition().name().equals("knowledge.search"))
                        .findFirst()
                        .orElseThrow();
                var result =
                    new tools.jackson.databind.ObjectMapper()
                        .readTree(callback.call("{\"query\":\"" + marker + "\",\"limit\":1}"));
                assertThat(result.path("status").stringValue("")).isEqualTo("OK");
                var evidence = result.path("sources").get(0);
                return evidence.path("excerpt").stringValue("")
                    + " [source]("
                    + evidence.path("citation").stringValue("")
                    + ")";
              })
          .when(model)
          .conversational(
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyList());
      org.mockito.Mockito.clearInvocations(model);
      String answer =
          actors.call(
              owner,
              AccountRole.MEMBER,
              () -> agent.respondTo("web-" + UUID.randomUUID(), question));
      assertThat(answer).contains(document[1], "/knowledge/" + source.id().value());
      assertThat(audit.recentEntries())
          .anySatisfy(
              entry -> {
                assertThat(entry.providerId()).isEqualTo("knowledge");
                assertThat(entry.toolName()).isEqualTo("knowledge.search");
                assertThat(entry.actorId()).isEqualTo(owner.accountId().value().toString());
                assertThat(entry.resultPreview()).isNull();
              });
      String response =
          actors.call(
              owner, AccountRole.MEMBER, () -> tools.getSource(source.id().value().toString()));
      assertThat(response)
          .contains(document[1], "/knowledge/" + source.id().value(), "\"derivationVersion\":1");
      assertThat(
              actors.call(
                  reader,
                  AccountRole.MEMBER,
                  () -> tools.getSource(source.id().value().toString())))
          .contains("NOT_FOUND")
          .doesNotContain(document[1]);
      lifecycle.changeVisibility(owner, source.id(), KnowledgeVisibility.GROUP_SHARED);
      assertThat(
              actors.call(
                  reader,
                  AccountRole.MEMBER,
                  () -> tools.getSource(source.id().value().toString())))
          .contains(document[1]);
      lifecycle.changeVisibility(owner, source.id(), KnowledgeVisibility.PRIVATE);
      assertThat(actors.call(reader, AccountRole.MEMBER, () -> tools.search(marker, 8)))
          .doesNotContain(document[1]);
      lifecycle.hardDelete(owner, source.id());
      assertThat(
              actors.call(
                  owner, AccountRole.MEMBER, () -> tools.getSource(source.id().value().toString())))
          .contains("NOT_FOUND");
    }
  }

  @Test
  void privateCandidatesCannotStarveAuthorizedSearchAndExcerptsAreBounded() {
    Actor owner = actor();
    Actor outsider = actor();
    String query = "bounded" + UUID.randomUUID().toString().replace("-", "");
    var visible = extracted(owner, query + " visible " + "x".repeat(5000));
    for (int i = 0; i < 10; i++) extracted(outsider, query + " PRIVATE_CONTENT");
    String response = actors.call(owner, AccountRole.MEMBER, () -> tools().search(query, 1));
    assertThat(response)
        .contains(visible.value().toString(), "\"truncated\":true")
        .doesNotContain("PRIVATE_CONTENT", "x".repeat(2001));
  }

  private KnowledgeSourceId extracted(Actor actor, String text) {
    var source =
        lifecycle.register(
            actor, "fixture.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8));
    var candidate = lifecycle.beginReprocessing(actor, source.id(), "fixture", "1");
    records.record(KnowledgeExtractionRecord.succeeded(candidate, text));
    lifecycle.completeReprocessing(actor, candidate, true);
    return source.id();
  }

  private Actor actor() {
    return new Actor(
        accounts
            .create(
                "evidence-" + UUID.randomUUID().toString().substring(0, 8),
                "FixturePassword-123",
                AccountRole.MEMBER)
            .id());
  }

  private KnowledgeAgentTools tools() {
    return selection.bootstrapTools().stream()
        .filter(KnowledgeAgentTools.class::isInstance)
        .map(KnowledgeAgentTools.class::cast)
        .findFirst()
        .orElseThrow();
  }
}
