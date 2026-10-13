package org.zalava.assistant.chat.attachment.adapter.out.knowledge;

import java.util.UUID;
import org.zalava.assistant.chat.attachment.application.port.out.KnowledgeImportPort;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeIngestion;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/**
 * Delegates a durable chat attachment import to Zalava's existing owner-scoped knowledge pipeline.
 */
public final class KnowledgeImportAdapter implements KnowledgeImportPort {
  private final KnowledgeIngestion ingestion;
  private final KnowledgeSourceStore sources;

  public KnowledgeImportAdapter(KnowledgeIngestion ingestion) {
    this(ingestion, null);
  }

  public KnowledgeImportAdapter(KnowledgeIngestion ingestion, KnowledgeSourceStore sources) {
    this.ingestion = ingestion;
    this.sources = sources;
  }

  @Override
  public boolean sourceAvailable(Actor actor, String sourceId) {
    return sources == null
        || sources
            .findById(new KnowledgeSourceId(UUID.fromString(sourceId)))
            .filter(source -> source.owner().equals(actor))
            .isPresent();
  }

  @Override
  public ImportedSource importSource(
      Actor actor, String displayName, String contentType, byte[] content) {
    KnowledgeSource source = ingestion.submit(actor, displayName, contentType, content);
    return new ImportedSource(source.id().value().toString());
  }
}
