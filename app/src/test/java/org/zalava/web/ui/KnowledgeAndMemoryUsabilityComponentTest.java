package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.zalava.api.extensions.content.ContentExtractionFailureCategory;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.knowledge.memory.application.port.out.ActorMemoryStore;
import org.zalava.knowledge.memory.domain.MemoryDraft;
import org.zalava.knowledge.memory.domain.MemoryScope;
import org.zalava.support.SecureZalavaComponentTest;

@SecureZalavaComponentTest
class KnowledgeAndMemoryUsabilityComponentTest {
  private static final String PASSWORD = "UsabilityComponentPassword-123";
  @Autowired MockMvc mockMvc;
  @Autowired AccountLifecycle accounts;
  @Autowired KnowledgeSourceStore sources;
  @Autowired ActorMemoryStore memories;
  @Autowired KnowledgeSourceLifecycle lifecycle;
  @Autowired KnowledgeExtractionRecordStore extractionRecords;
  private Account owner;
  private Account other;

  @BeforeEach
  void createActors() {
    owner = activated("owner-");
    other = activated("other-");
  }

  @Test
  void renderedMakePrivateRevokesOtherActorsAccessAndConfirmsIt() throws Exception {
    var source = source(owner, KnowledgeVisibility.GROUP_SHARED);
    mockMvc
        .perform(get("/knowledge/" + source.id().value()).with(as(other)))
        .andExpect(status().isOk());
    String html = knowledgeHtml(owner);
    var form =
        Pattern.compile(
                "<form[^>]*action=\"/knowledge/"
                    + source.id().value()
                    + "/visibility\"[^>]*>(.*?)</form>",
                Pattern.DOTALL)
            .matcher(html);
    assertThat(form.find()).isTrue();
    var input = Pattern.compile("<input[^>]*name=\"shared\"[^>]*>").matcher(form.group(1));
    assertThat(input.find()).isTrue();
    var value = Pattern.compile("value=\"([^\"]*)\"").matcher(input.group());
    String submitted = value.find() ? value.group(1) : "";
    var result =
        mockMvc
            .perform(
                post("/knowledge/" + source.id().value() + "/visibility")
                    .with(as(owner))
                    .with(csrf())
                    .param("shared", submitted))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(sources.findById(source.id()).orElseThrow().visibility())
        .isEqualTo(KnowledgeVisibility.PRIVATE);
    mockMvc
        .perform(get("/knowledge/" + source.id().value()).with(as(other)))
        .andExpect(status().isNotFound());
    assertThat(redirectedHtml(result, owner))
        .contains("Source sharing updated.", "role=\"status\"");
  }

  @Test
  void sharedSourceIsReadOnlyForOtherActorsAndDeniedMutationHasVisibleFeedback() throws Exception {
    var source = source(owner, KnowledgeVisibility.GROUP_SHARED);
    String base = "/knowledge/" + source.id().value();
    assertThat(knowledgeHtml(other))
        .contains(source.displayName(), "Shared read-only")
        .doesNotContain(
            "action=\"" + base + "/retry",
            "action=\"" + base + "/visibility",
            "action=\"" + base + "/delete");
    var result =
        mockMvc
            .perform(post(base + "/delete").with(as(other)).with(csrf()).param("confirmed", "true"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(sources.findById(source.id())).isPresent();
    assertThat(redirectedHtml(result, other))
        .contains("Source deletion could not be completed.", "role=\"alert\"");
  }

  @Test
  void uploadFeedbackExplainsThePendingProcessingPrerequisite() throws Exception {
    var file =
        new MockMultipartFile(
            "file",
            "component-document.txt",
            "text/plain",
            "A bounded synthetic document.".getBytes(StandardCharsets.UTF_8));
    var result =
        mockMvc
            .perform(multipart("/knowledge/upload").file(file).with(as(owner)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(redirectedHtml(result, owner))
        .contains(
            "Source uploaded for processing.",
            "Document processing module required",
            "role=\"status\"");
    var uploaded =
        sources.visibleTo(new Actor(owner.id())).stream()
            .filter(s -> s.displayName().equals("component-document.txt"))
            .findFirst()
            .orElseThrow();
    assertThat(uploaded.processingState()).isEqualTo(SourceProcessingState.PENDING);
    String detail =
        mockMvc
            .perform(get("/knowledge/" + uploaded.id().value()).with(as(owner)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(detail)
        .contains(
            "Install and start a document-processing module", "Ask your workspace administrator");
  }

  @Test
  void uploadFailureIsVisibleWithoutCreatingASource() throws Exception {
    var file =
        new MockMultipartFile(
            "file", "invalid.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));
    var result =
        mockMvc
            .perform(multipart("/knowledge/upload").file(file).with(as(owner)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(redirectedHtml(result, owner))
        .contains("Source upload could not be completed.", "role=\"alert\"");
    assertThat(sources.visibleTo(new Actor(owner.id())))
        .filteredOn(source -> source.owner().equals(new Actor(owner.id())))
        .isEmpty();
  }

  @Test
  void sourceOwnerCanDiscoverRetryAndDeleteResults() throws Exception {
    var source = source(owner, KnowledgeVisibility.PRIVATE);
    String base = "/knowledge/" + source.id().value();
    var retry =
        mockMvc
            .perform(post(base + "/retry").with(as(owner)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(redirectedHtml(retry, owner))
        .contains("Source reprocessing requested.", "role=\"status\"");
    var deleted =
        mockMvc
            .perform(post(base + "/delete").with(as(owner)).with(csrf()).param("confirmed", "true"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(redirectedHtml(deleted, owner)).contains("Source deleted.", "role=\"status\"");
    assertThat(sources.findById(source.id())).isEmpty();
  }

  @Test
  void knowledgeFiltersHaveReadableChoicesAndFilterRealRecords() throws Exception {
    var source = source(owner, KnowledgeVisibility.PRIVATE);
    String html =
        mockMvc
            .perform(
                get("/knowledge")
                    .with(as(owner))
                    .param("contentType", "text/plain")
                    .param("processingState", "READY"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(html)
        .contains(
            source.displayName(),
            "File type",
            "Processing status",
            "<select",
            "Plain text",
            "Ready");
    String filtered =
        mockMvc
            .perform(get("/knowledge").with(as(owner)).param("contentType", "application/pdf"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(filtered).doesNotContain(source.displayName());
  }

  @Test
  void memoryExplainsPrivateScopesAndFormatsDatesForPeople() throws Exception {
    var memory =
        memories.remember(
            new Actor(owner.id()),
            new MemoryDraft(MemoryScope.USER, "I prefer short replies.", Map.of()));
    String html =
        mockMvc
            .perform(get("/memory").with(as(owner)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(html)
        .contains(
            "Private to your account",
            "Personal preferences",
            "Project knowledge",
            "Agent knowledge",
            "Execution trace",
            "<time");
    assertThat(html).doesNotContain(">" + memory.createdAt() + "<");
    String detail =
        mockMvc
            .perform(get("/memory/" + memory.id()).with(as(owner)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(detail).contains("Private to your account", "Personal preferences", "<time");
    mockMvc.perform(get("/memory/" + memory.id()).with(as(other))).andExpect(status().isNotFound());
  }

  @Test
  void processingFailureExplainsTheCategoryWithoutExposingProcessorInternals() throws Exception {
    var source = source(owner, KnowledgeVisibility.PRIVATE);
    var candidate =
        lifecycle.beginReprocessing(new Actor(owner.id()), source.id(), "qa-parser", "1");
    extractionRecords.record(
        KnowledgeExtractionRecord.failed(
            candidate,
            ContentExtractionFailureCategory.MALFORMED_INPUT,
            "private processor stack detail"));
    lifecycle.completeReprocessing(new Actor(owner.id()), candidate, false);
    String detail =
        mockMvc
            .perform(get("/knowledge/" + source.id().value()).with(as(owner)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(detail)
        .contains(
            "Processing failed", "The document could not be read", "document-processing module")
        .doesNotContain("private processor stack detail");
    mockMvc
        .perform(get("/knowledge/" + source.id().value()).with(as(other)))
        .andExpect(status().isNotFound());
  }

  private String knowledgeHtml(Account account) throws Exception {
    return mockMvc
        .perform(get("/knowledge").with(as(account)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String redirectedHtml(MvcResult result, Account account) throws Exception {
    var request = get(result.getResponse().getRedirectedUrl()).with(as(account));
    if (!result.getFlashMap().isEmpty()) request.flashAttrs(result.getFlashMap());
    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private Account activated(String prefix) {
    var account = accounts.create(prefix + UUID.randomUUID(), PASSWORD, AccountRole.MEMBER);
    accounts.changePassword(account.id(), PASSWORD, PASSWORD + "-changed");
    return accounts.findById(account.id()).orElseThrow();
  }

  private static RequestPostProcessor as(Account account) {
    return user(account.loginName()).roles("MEMBER");
  }

  private KnowledgeSource source(Account account, KnowledgeVisibility visibility) {
    Instant created = Instant.parse("2026-09-09T19:00:00Z");
    return sources.register(
        new KnowledgeSource(
            KnowledgeSourceId.create(),
            new Actor(account.id()),
            "notes-" + UUID.randomUUID() + ".txt",
            "text/plain",
            19,
            "f".repeat(64),
            visibility,
            SourceProcessingState.READY,
            created,
            created,
            0));
  }
}
