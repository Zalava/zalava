package org.zalava.assistant.chat.attachment;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.zalava.assistant.chat.attachment.adapter.out.filesystem.FileSystemChatAttachmentStore;
import org.zalava.assistant.chat.attachment.adapter.out.knowledge.KnowledgeImportAdapter;
import org.zalava.assistant.chat.attachment.application.ChatAttachments;
import org.zalava.assistant.chat.attachment.application.port.out.ChatAttachmentStore;
import org.zalava.assistant.chat.attachment.application.port.out.KnowledgeImportPort;
import org.zalava.knowledge.application.KnowledgeIngestion;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

@Configuration
class ChatAttachmentConfiguration {

  @Bean
  ChatAttachmentStore chatAttachmentStore(
      @Value("${zalava.chat.attachment.managed-data-root:}") String managedDataRoot,
      @Value("${agent.workspace:Unknown}") Resource workspace)
      throws IOException {
    return new FileSystemChatAttachmentStore(
        managedDataRoot.isBlank() ? workspace.getFilePath() : Path.of(managedDataRoot));
  }

  @Bean
  KnowledgeImportPort chatKnowledgeImportPort(
      KnowledgeIngestion ingestion, KnowledgeSourceStore sources) {
    return new KnowledgeImportAdapter(ingestion, sources);
  }

  @Bean
  ChatAttachments chatAttachments(
      ChatAttachmentStore store,
      KnowledgeImportPort knowledgeImports,
      RuntimeQueries runtime,
      @Value("${zalava.chat.attachment.maximum-upload-bytes:5242880}") long maximumUploadBytes) {
    return new ChatAttachments(store, knowledgeImports, maximumUploadBytes, runtime);
  }
}
