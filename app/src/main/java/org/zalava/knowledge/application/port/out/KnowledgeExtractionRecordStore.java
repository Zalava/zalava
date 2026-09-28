package org.zalava.knowledge.application.port.out;

import java.util.Optional;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSourceId;

public interface KnowledgeExtractionRecordStore {
  void record(KnowledgeExtractionRecord record);

  Optional<KnowledgeExtractionRecord> find(KnowledgeSourceId sourceId, long derivationVersion);
}
