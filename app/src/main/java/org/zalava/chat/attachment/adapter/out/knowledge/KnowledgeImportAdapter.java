package org.zalava.chat.attachment.adapter.out.knowledge;

import org.zalava.accounts.domain.Actor;
import org.zalava.chat.attachment.application.port.out.KnowledgeImportPort;
import org.zalava.knowledge.application.KnowledgeIngestion;
import org.zalava.knowledge.domain.KnowledgeSource;

/** Delegates a durable chat attachment import to SEA's existing owner-scoped knowledge pipeline. */
public final class KnowledgeImportAdapter implements KnowledgeImportPort {
  private final KnowledgeIngestion ingestion;

  public KnowledgeImportAdapter(KnowledgeIngestion ingestion) {
    this.ingestion = ingestion;
  }

  @Override
  public ImportedSource importSource(
      Actor actor, String displayName, String contentType, byte[] content) {
    KnowledgeSource source = ingestion.submit(actor, displayName, contentType, content);
    return new ImportedSource(source.id().value().toString());
  }
}
