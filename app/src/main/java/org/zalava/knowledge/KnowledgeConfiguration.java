package org.zalava.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.api.extensions.content.ContentExtractionLimits;
import org.zalava.knowledge.adapter.out.filesystem.FileSystemKnowledgeBlobStore;
import org.zalava.knowledge.adapter.out.jdbc.JdbcKnowledgeAuditStore;
import org.zalava.knowledge.adapter.out.jdbc.JdbcKnowledgeDerivationStore;
import org.zalava.knowledge.adapter.out.jdbc.JdbcKnowledgeExtractionRecordStore;
import org.zalava.knowledge.adapter.out.jdbc.JdbcKnowledgeSearchStore;
import org.zalava.knowledge.adapter.out.jdbc.JdbcKnowledgeSourceStore;
import org.zalava.knowledge.application.KnowledgeExtractionJob;
import org.zalava.knowledge.application.KnowledgeIngestion;
import org.zalava.knowledge.application.KnowledgeLibrary;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeAuditStore;
import org.zalava.knowledge.application.port.out.KnowledgeBlobStore;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.application.port.out.KnowledgeIngestionScheduler;
import org.zalava.knowledge.application.port.out.KnowledgeSearchStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

@Configuration
class KnowledgeConfiguration {

  @Bean
  org.zalava.knowledge.application.KnowledgeEvidenceQueries knowledgeEvidenceQueries(
      JdbcClient jdbc) {
    return new org.zalava.knowledge.application.KnowledgeEvidenceQueries(
        new org.zalava.knowledge.adapter.out.jdbc.JdbcKnowledgeEvidenceStore(jdbc));
  }

  @Bean
  ContentExtractionLimits knowledgeExtractionLimits(
      @Value("${zalava.knowledge.maximum-upload-bytes:10485760}") long maximumUploadBytes,
      @Value("${zalava.knowledge.maximum-text-characters:1000000}") int maximumTextCharacters) {
    return new ContentExtractionLimits(maximumUploadBytes, maximumTextCharacters, 100, 4096, 1000);
  }

  @Bean
  KnowledgeIngestion knowledgeIngestion(
      KnowledgeSourceLifecycle lifecycle,
      KnowledgeIngestionScheduler scheduler,
      ContentExtractionLimits limits) {
    return new KnowledgeIngestion(lifecycle, scheduler, limits.maximumInputBytes());
  }

  @Bean
  KnowledgeExtractionJob knowledgeExtractionJob(
      KnowledgeSourceLifecycle lifecycle,
      KnowledgeSourceStore sources,
      KnowledgeBlobStore blobs,
      KnowledgeExtractionRecordStore records,
      RuntimeQueries runtime,
      ContentExtractionLimits limits) {
    return new KnowledgeExtractionJob(lifecycle, sources, blobs, records, runtime, limits);
  }

  @Bean
  KnowledgeSourceStore knowledgeSourceStore(JdbcClient jdbc) {
    return new JdbcKnowledgeSourceStore(jdbc);
  }

  @Bean
  KnowledgeDerivationStore knowledgeDerivationStore(JdbcClient jdbc) {
    return new JdbcKnowledgeDerivationStore(jdbc);
  }

  @Bean
  KnowledgeExtractionRecordStore knowledgeExtractionRecordStore(JdbcClient jdbc) {
    return new JdbcKnowledgeExtractionRecordStore(jdbc);
  }

  @Bean
  KnowledgeSearchStore knowledgeSearchStore(JdbcClient jdbc) {
    return new JdbcKnowledgeSearchStore(jdbc);
  }

  @Bean
  KnowledgeLibrary knowledgeLibrary(KnowledgeSourceStore sources, KnowledgeSearchStore search) {
    return new KnowledgeLibrary(sources, search);
  }

  @Bean
  KnowledgeAuditStore knowledgeAuditStore(JdbcClient jdbc) {
    return new JdbcKnowledgeAuditStore(jdbc);
  }

  @Bean
  KnowledgeSourceLifecycle knowledgeSourceLifecycle(
      KnowledgeSourceStore sources,
      KnowledgeDerivationStore derivations,
      KnowledgeBlobStore blobs,
      KnowledgeAuditStore audit,
      org.zalava.platform.observability.application.port.out.OperationalMetrics metrics) {
    return new org.zalava.knowledge.adapter.out.transaction.TransactionalKnowledgeSourceLifecycle(
        sources, derivations, blobs, audit, java.time.Clock.systemUTC(), metrics);
  }

  @Bean
  KnowledgeBlobStore knowledgeBlobStore(
      @Value("${zalava.knowledge.managed-data-root:}") String managedDataRoot,
      @Value("${agent.workspace:Unknown}") Resource workspace)
      throws java.io.IOException {
    return new FileSystemKnowledgeBlobStore(
        managedDataRoot.isBlank()
            ? workspace.getFilePath()
            : java.nio.file.Path.of(managedDataRoot));
  }
}
