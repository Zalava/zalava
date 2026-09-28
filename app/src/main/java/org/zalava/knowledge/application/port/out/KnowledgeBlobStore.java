package org.zalava.knowledge.application.port.out;

import java.util.Optional;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/** SEA-owned original-byte boundary. No module receives the managed-root path. */
public interface KnowledgeBlobStore {
  BlobReceipt write(KnowledgeSourceId sourceId, byte[] content);

  Optional<byte[]> read(KnowledgeSourceId sourceId);

  void delete(KnowledgeSourceId sourceId);

  record BlobReceipt(long byteCount, String sha256) {}
}
